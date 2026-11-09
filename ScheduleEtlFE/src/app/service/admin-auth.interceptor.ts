import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { AdminAuthService } from './admin-auth.service';

export const adminAuthInterceptor: HttpInterceptorFn = (request, next) => {
  const authorization = inject(AdminAuthService).authorizationHeader();
  if (!authorization || !request.url.startsWith('/api/v1/')) return next(request);
  return next(request.clone({ setHeaders: { Authorization: authorization } }));
};
