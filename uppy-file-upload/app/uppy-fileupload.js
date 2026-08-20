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
  .use(Tus, { endpoint: resolvedEndpoint });

  uppyInstance.on('upload-success', function(file, upload) {
      console.log("Upload " + file.name + " completed with URL " + upload.url);
      console.log("Developer: Now pass URL " + upload.url + " to the backend or dynamically add it to an existing form!");
  });

  return uppyInstance;
}