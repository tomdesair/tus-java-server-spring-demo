import '@uppy/core/css/style.min.css';
import '@uppy/dashboard/css/style.min.css';
import Uppy from '@uppy/core';
import Dashboard from '@uppy/dashboard';
import Tus from '@uppy/tus';

export function uppy() {
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
  .use(Tus, { endpoint: 'http://localhost:8080/test/api/upload' });

  uppyInstance.on('upload-success', function(file, upload) {
      console.log("Upload " + file.name + " completed with URL " + upload.url);
      console.log("Developer: Now pass URL " + upload.url + " to the backend or dynamically add it to an existing form!");
  });

  return uppyInstance;
}