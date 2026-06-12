package me.desair.spring.tus;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import me.desair.tus.server.TusFileUploadService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

@Controller
@RequestMapping(value = "/api/upload")
//access Cros
@CrossOrigin(origins = "*")
public class FileUploadController {

    @Autowired
    private TusFileUploadService tusFileUploadService;

    @RequestMapping(value = {"", "/**"}, method = {RequestMethod.POST, RequestMethod.PATCH, RequestMethod.HEAD,
            RequestMethod.DELETE, RequestMethod.OPTIONS, RequestMethod.GET})
    public void processUpload(final HttpServletRequest servletRequest, final HttpServletResponse servletResponse) throws IOException {
        tusFileUploadService.process(servletRequest, servletResponse);
        //access response header Location,Upload-Offset,Upload-length
        servletResponse.addHeader("Access-Control-Expose-Headers","Location,Upload-Offset,Upload-Length");
    }

}
