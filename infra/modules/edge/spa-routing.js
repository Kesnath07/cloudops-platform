// Viewer-request function for the static site behaviour: client-side routes such as
// /incidents/123 have no file in S3, so any path without a file extension is served the SPA shell.
// The /api/* behaviour does not use this function, so API 404s stay real 404s.
function handler(event) {
  var request = event.request;
  var lastSegment = request.uri.substring(request.uri.lastIndexOf('/') + 1);
  if (lastSegment.indexOf('.') === -1) {
    request.uri = '/index.html';
  }
  return request;
}
