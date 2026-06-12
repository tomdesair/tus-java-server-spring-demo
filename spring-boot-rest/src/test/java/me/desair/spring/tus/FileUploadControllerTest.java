package me.desair.spring.tus;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import me.desair.tus.server.TusFileUploadService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
public class FileUploadControllerTest {

    @Autowired
    private TusFileUploadService tusFileUploadService;

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void contextLoads() {
        assertNotNull(tusFileUploadService, "TusFileUploadService should be instantiated");
    }

    @Test
    public void testUploadEndpointOptions() throws Exception {
        // TUS OPTIONS request should return 204 No Content
        mockMvc.perform(options("/api/upload")
                        .header("Tus-Resumable", "1.0.0"))
                .andExpect(status().isNoContent());
    }

    @Test
    public void testUploadEndpointPostWithoutHeaders() throws Exception {
        // A standard POST request without Tus headers should be rejected by the Tus service (status 412)
        // because the required Tus-Resumable header is missing.
        mockMvc.perform(post("/api/upload"))
                .andExpect(status().isPreconditionFailed());
    }
}
