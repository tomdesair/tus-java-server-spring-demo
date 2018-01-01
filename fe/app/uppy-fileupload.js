module.exports = {
  uppy: function() {
    const Uppy = require('uppy/lib/core')
    const Dashboard = require('uppy/lib/plugins/Dashboard')
    const GoogleDrive = require('uppy/lib/plugins/GoogleDrive')
    const Dropbox = require('uppy/lib/plugins/Dropbox')
    const Instagram = require('uppy/lib/plugins/Instagram')
    const Webcam = require('uppy/lib/plugins/Webcam')
    const Tus = require('uppy/lib/plugins/Tus')

    const uppy = Uppy({
      debug: true,
      autoProceed: false,
      restrictions: {
        maxFileSize: 1000000,
        maxNumberOfFiles: 3,
        minNumberOfFiles: 2,
        allowedFileTypes: ['image/*', 'video/*']
      }
    })
    .use(Dashboard, {
      inline: true,
      target: '.DashboardContainer',
      replaceTargetContent: true,
      note: 'Images and video only, 2–3 files, up to 1 MB',
      maxHeight: 450,
      metaFields: [
        { id: 'license', name: 'License', placeholder: 'specify license' },
        { id: 'caption', name: 'Caption', placeholder: 'describe what the image is about' }
      ]
    })
    .use(Webcam, { target: Dashboard })
    .use(Tus, { endpoint: 'https://master.tus.io/files/' })

    return uppy;
  }
}