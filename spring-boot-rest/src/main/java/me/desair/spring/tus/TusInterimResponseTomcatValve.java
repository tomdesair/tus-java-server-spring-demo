package me.desair.spring.tus;

import jakarta.servlet.ServletException;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import me.desair.tus.server.TusFileUploadService;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.valves.ValveBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tomcat Valve implementation that inspects incoming HTTP requests using {@link
 * TusFileUploadService#getRawInterimResponse(jakarta.servlet.http.HttpServletRequest, String)} and writes
 * raw HTTP 104 interim response frames directly to Tomcat's underlying TCP socket before servlet
 * execution.
 *
 * <h2>Architectural Reasoning & Container Limitations</h2>
 *
 * <p>Standard Servlet specification (Jakarta Servlet 6.0) and Tomcat Catalina container APIs
 * enforce a strict HTTP response lifecycle (Status Line &rarr; Headers &rarr; Response Body) via
 * {@link jakarta.servlet.http.HttpServletResponse} and {@link jakarta.servlet.ServletOutputStream}.
 * Attempting to write bytes via standard output streams will treat those bytes as part of the
 * response body after committing status code 200, rather than emitting preliminary 1xx
 * informational response lines (such as {@code HTTP/1.1 104 Resumption}).
 *
 * <p>While Tomcat provides internal APIs like {@code Response#sendAcknowledgement()} (via Coyote
 * {@code ActionCode.ACK}), that mechanism is hardcoded exclusively for HTTP 100 Continue frames.
 * Tomcat 10 / Servlet 6.0 does not expose a public, non-reflective API for writing arbitrary raw
 * 1xx interim status frames directly to the TCP socket before servlet dispatch.
 *
 * <h2>Reflection & Performance Optimization</h2>
 *
 * <p>To emit raw HTTP 104 frames, this valve retrieves Tomcat's low-level {@code SocketWrapperBase}
 * from Coyote's internal {@code Http11OutputBuffer}. To eliminate per-request reflection overhead,
 * target {@link Field} and {@link Method} instances are resolved and cached statically upon first
 * execution.
 *
 * <h2>Production Best Practices & Upgrade Path</h2>
 *
 * <ul>
 *   <li><b>Edge Proxies / Gateways:</b> In production environments, preliminary 1xx responses (such
 *       as 103 Early Hints or 104 Resumption) are typically emitted at the Edge Proxy / API Gateway
 *       layer (e.g. Nginx, HAProxy, Envoy, Cloudflare), which operates directly on raw TCP streams
 *       without risking servlet container state machine desynchronization.
 *   <li><b>Servlet 6.2 / Tomcat 12+:</b> Standardized APIs like {@code
 *       HttpServletResponse#sendEarlyHints()} in future Servlet specifications provide native 1xx
 *       support, allowing reflection-free interim response emission once adopted.
 * </ul>
 */
public class TusInterimResponseTomcatValve extends ValveBase {

  private static final Logger LOG = LoggerFactory.getLogger(TusInterimResponseTomcatValve.class);

  // Cached reflection fields and methods to avoid per-request lookup overhead
  private static Field outputBufferField;
  private static Field socketWrapperField;
  private static Method writeMethod;
  private static Method flushMethod;

  private final TusFileUploadService tusFileUploadService;

  /**
   * Constructs a new Tomcat Valve for emitting HTTP 104 interim responses.
   *
   * @param tusFileUploadService the upload service used to generate raw interim response frames
   */
  public TusInterimResponseTomcatValve(TusFileUploadService tusFileUploadService) {
    this.tusFileUploadService = tusFileUploadService;
  }

  @Override
  public void invoke(Request request, Response response) throws IOException, ServletException {
    if (tusFileUploadService != null) {
      try {
        // Step 1: Inspect the incoming request to check if a 104 interim response frame is needed
        String rawInterimResponse =
            tusFileUploadService.getRawInterimResponse(request.getRequest(), null);

        if (rawInterimResponse != null) {
          byte[] bytes = rawInterimResponse.getBytes(StandardCharsets.UTF_8);

          // Step 2: Write raw HTTP 104 bytes directly to Tomcat's underlying SocketWrapperBase
          boolean written = writeToSocketWrapper(response, bytes);
          if (written) {
            LOG.info(
                "Emitted raw HTTP 104 Interim Response via Tomcat SocketWrapper for request URI: {}",
                request.getRequestURI());
          } else {
            LOG.warn("Could not obtain Tomcat SocketWrapper to emit 104 interim response");
          }
        }
      } catch (Exception e) {
        LOG.warn("Error emitting HTTP 104 interim response in Tomcat Valve", e);
      }
    }

    // Step 3: Continue normal request processing down Tomcat's pipeline to the target servlet
    getNext().invoke(request, response);
  }

  /**
   * Writes raw bytes directly to Tomcat's underlying {@code SocketWrapperBase}.
   *
   * <p>ponytail: Tomcat encapsulates SocketWrapperBase inside Http11OutputBuffer without exposing a
   * public raw 1xx writing API in Tomcat 10. Reflection lookups are cached to eliminate per-request
   * reflection overhead. Upgrade path: Replace with Tomcat 12+ sendEarlyHints / Servlet 6.2 1xx
   * APIs once standard.
   *
   * @param response Tomcat's Catalina Response connector object
   * @param bytes the UTF-8 encoded HTTP 104 frame bytes to write
   * @return true if successfully written and flushed to the socket; false otherwise
   */
  private boolean writeToSocketWrapper(Response response, byte[] bytes) {
    try {
      // Access low-level Coyote response object
      org.apache.coyote.Response coyoteResponse = response.getCoyoteResponse();

      // Extract low-level output buffer object (e.g. Http11OutputBuffer)
      Object outputBuffer = getOutputBuffer(coyoteResponse);
      if (outputBuffer != null) {

        // Extract SocketWrapperBase instance from output buffer
        Object socketWrapper = getSocketWrapper(outputBuffer);
        if (socketWrapper != null) {

          // Invoke socketWrapper.write(...) and socketWrapper.flush(...)
          return invokeWriteAndFlush(socketWrapper, bytes);
        }
      }
    } catch (Exception e) {
      LOG.warn("Failed to write to Tomcat SocketWrapperBase via reflection", e);
    }
    return false;
  }

  /**
   * Resolves and caches the private {@code outputBuffer} field on {@link
   * org.apache.coyote.Response}.
   *
   * @param coyoteResponse the low-level Coyote response instance
   * @return the internal output buffer object, or null if unresolvable
   * @throws Exception if reflection access fails
   */
  private Object getOutputBuffer(org.apache.coyote.Response coyoteResponse) throws Exception {
    if (outputBufferField == null) {
      Field field = org.apache.coyote.Response.class.getDeclaredField("outputBuffer");
      field.setAccessible(true);
      outputBufferField = field;
    }
    return outputBufferField.get(coyoteResponse);
  }

  /**
   * Traverses the class hierarchy of the output buffer to resolve and cache the {@code
   * socketWrapper} field.
   *
   * @param outputBuffer the output buffer instance (e.g., Http11OutputBuffer)
   * @return the SocketWrapperBase instance, or null if unresolvable
   * @throws Exception if reflection access fails
   */
  private Object getSocketWrapper(Object outputBuffer) throws Exception {
    if (socketWrapperField == null
        || !socketWrapperField.getDeclaringClass().isAssignableFrom(outputBuffer.getClass())) {
      Field field = findDeclaredField(outputBuffer.getClass(), "socketWrapper");
      if (field != null) {
        field.setAccessible(true);
        socketWrapperField = field;
      }
    }
    return socketWrapperField != null ? socketWrapperField.get(outputBuffer) : null;
  }

  /**
   * Resolves and invokes {@code write(boolean block, byte[] b, int off, int len)} and {@code
   * flush(boolean block)} on Tomcat's low-level {@code SocketWrapperBase}.
   *
   * @param socketWrapper the SocketWrapperBase instance
   * @param bytes the raw HTTP bytes to transmit
   * @return true if write and flush succeeded
   * @throws Exception if reflection invocation fails
   */
  private boolean invokeWriteAndFlush(Object socketWrapper, byte[] bytes) throws Exception {
    if (writeMethod == null) {
      // Resolve SocketWrapperBase.write(boolean block, byte[] b, int off, int len)
      writeMethod =
          socketWrapper
              .getClass()
              .getMethod("write", boolean.class, byte[].class, int.class, int.class);
    }
    if (flushMethod == null) {
      // Resolve SocketWrapperBase.flush(boolean block)
      flushMethod = socketWrapper.getClass().getMethod("flush", boolean.class);
    }

    // Write bytes synchronously (block = true) directly into socket buffer
    writeMethod.invoke(socketWrapper, true, bytes, 0, bytes.length);

    // Flush socket buffer immediately to send raw 104 frame across the wire
    flushMethod.invoke(socketWrapper, true);
    return true;
  }

  /**
   * Helper utility to search for a declared field up through a class inheritance hierarchy.
   *
   * @param clazz the target class
   * @param fieldName the name of the field to find
   * @return the Field instance if found, or null
   */
  private static Field findDeclaredField(Class<?> clazz, String fieldName) {
    Class<?> current = clazz;
    while (current != null) {
      try {
        return current.getDeclaredField(fieldName);
      } catch (NoSuchFieldException e) {
        current = current.getSuperclass();
      }
    }
    return null;
  }
}
