package me.desair.spring.tus;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
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

  @Test
  public void testInvokeWithMultipleServicesPassesThrough() throws Exception {
    TusFileUploadService service1 = mock(TusFileUploadService.class);
    TusFileUploadService service2 = mock(TusFileUploadService.class);
    when(service1.getRawInterimResponse(any(HttpServletRequest.class), any())).thenReturn(null);
    when(service2.getRawInterimResponse(any(HttpServletRequest.class), any())).thenReturn(null);

    TusInterimResponseTomcatValve valve =
        new TusInterimResponseTomcatValve(List.of(service1, service2));
    Valve nextValve = mock(Valve.class);
    valve.setNext(nextValve);

    Request request = mock(Request.class);
    Response response = mock(Response.class);
    HttpServletRequest servletRequest = mock(HttpServletRequest.class);
    when(request.getRequest()).thenReturn(servletRequest);

    valve.invoke(request, response);

    verify(nextValve).invoke(request, response);
  }

  @Test
  public void testInvokeWithNullServiceListPassesThrough() throws Exception {
    TusInterimResponseTomcatValve valve =
        new TusInterimResponseTomcatValve((List<TusFileUploadService>) null);
    Valve nextValve = mock(Valve.class);
    valve.setNext(nextValve);

    Request request = mock(Request.class);
    Response response = mock(Response.class);

    valve.invoke(request, response);

    verify(nextValve).invoke(request, response);
  }
}
