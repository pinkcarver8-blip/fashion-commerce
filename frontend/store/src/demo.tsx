import '@commerce/api-client/admin';

export function rememberAdminPath() {
  localStorage.setItem('path', '/api/admin/orders');
}

export function RawHtml() {
  return <div dangerouslySetInnerHTML={{ __html: '<b>x</b>' }} />;
}
