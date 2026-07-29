package me.desair.spring.tus;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import me.desair.tus.server.TusFileUploadService;
import org.apache.catalina.Valve;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.junit.jupiter.api.Test;

public class TusInterimResponseTomcatValveTest {

  @Test
  public void testInvokePassesThroughWhenNoInterimResponse() throws Exception {
    TusFileUploadService service = mock(TusFileUploadService.class);
    when(service.getRawInterimResponse(any(HttpServletRequest.class), any())).thenReturn(null);

    TusInterimResponseTomcatValve valve = new TusInterimResponseTomcatValve(service);
    Valve nextValve = mock(Valve.class);
    valve.setNext(nextValve);

    Request request = mock(Request.class);
    Response response = mock(Response.class);
    HttpServletRequest servletRequest = mock(HttpServletRequest.class);
    when(request.getRequest()).thenReturn(servletRequest);

    valve.invoke(request, response);

    verify(nextValve).invoke(request, response);
  }
}
