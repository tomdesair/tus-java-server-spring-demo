import '@uppy/core/css/style.min.css';
import '@uppy/dashboard/css/style.min.css';
import Uppy from '@uppy/core';
import Dashboard from '@uppy/dashboard';
import Tus from '@uppy/tus';

/**
 * Initializes Uppy with Dashboard and Tus uploader plugins.
 *
 * Resolves the upload endpoint dynamically from the 'data-endpoint' HTML attribute
 * on the '.DashboardContainer' element, allowing each test page (/test/, /test-s3/,
 * /test-azure/) to target its respective storage backend endpoint seamlessly.
 *
 * @param {Object} [options] Optional configuration overrides
 * @returns {Uppy|null} Configured Uppy instance, or null if no Dashboard container exists
 */
export function uppy(options = {}) {
  const container = document.querySelector('.DashboardContainer');
  if (!container) {
    return null;
  }

  // Read data-endpoint attribute (e.g. "/test/api/upload", "/test-s3/api/upload", "/test-azure/api/upload")
  const endpointAttr = container.getAttribute('data-endpoint');
  const endpoint = options.endpoint || endpointAttr || '/test/api/upload';

  // Ensure full URL is constructed if relative path is provided
  const resolvedEndpoint =
      endpoint.startsWith('http://') || endpoint.startsWith('https://')
          ? endpoint
          : window.location.origin + endpoint;

  const uppyInstance = new Uppy({
    debug: true,
    autoProceed: false,
  })
  .use(Dashboard, {
    inline: true,
    target: '.DashboardContainer',
    replaceTargetContent: true,
    note: 'The best way to test this is with a file of several hundred MB (e.g. a Linux distro DVD image)',
    maxHeight: 450,
    metaFields: [
      { id: 'license', name: 'License', placeholder: 'specify license' },
      { id: 'caption', name: 'Caption', placeholder: 'describe what the image is about' }
    ]
  })
  .use(Tus, {
    endpoint: resolvedEndpoint,
    onBeforeRequest: async (req, file) => {
      // Compute and attach SHA-1 Upload-Checksum header on PATCH requests via Web Crypto API
      if (req && req.getMethod && req.getMethod() === 'PATCH' && file && file.data) {
        try {
          const offset = parseInt(req.getHeader('Upload-Offset') || '0', 10);
          const chunkData = file.data.slice(offset);
          const buffer = await chunkData.arrayBuffer();
          const hashBuffer = await window.crypto.subtle.digest('SHA-1', buffer);
          const hashBytes = new Uint8Array(hashBuffer);
          let binary = '';
          for (let i = 0; i < hashBytes.byteLength; i++) {
            binary += String.fromCharCode(hashBytes[i]);
          }
          const base64 = btoa(binary);
          req.setHeader('Upload-Checksum', 'sha1 ' + base64);
          console.log(
              '[Tus Checksum] Attaching Upload-Checksum for ' + file.name + ' at offset ' + offset
              + ': sha1 ' + base64);
        } catch (err) {
          console.warn('[Tus Checksum] Failed to compute Web Crypto SHA-1 checksum:', err);
        }
      }
    }
  });

  uppyInstance.on('upload-success', function(file, response) {
      const uploadURL = response?.uploadURL || file?.uploadURL;
      console.log("Upload " + file.name + " completed with URL " + uploadURL);
      console.log("Developer: Now pass URL " + uploadURL + " to the backend or dynamically add it to an existing form!");
  });

  return uppyInstance;
}