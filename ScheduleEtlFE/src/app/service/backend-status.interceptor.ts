import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { tap } from 'rxjs';
import { BackendStatusService } from './backend-status.service';

/** Il proxy di sviluppo risponde 500 senza corpo quando il backend non è raggiungibile. */
function isBackendDown(error: HttpErrorResponse): boolean {
  if (error.status === 0 || error.status === 502 || error.status === 503 || error.status === 504) return true;
  return error.status === 500 && (error.error === null || error.error === '' || typeof error.error === 'string' && !error.error.trim());
}

export const backendStatusInterceptor: HttpInterceptorFn = (request, next) => {
  if (!request.url.startsWith('/api/')) return next(request);
  const status = inject(BackendStatusService);
  return next(request).pipe(tap({
    next: (event) => {
      if ('status' in event) status.setUnavailable(false);
    },
    error: (error: unknown) => {
      if (error instanceof HttpErrorResponse) status.setUnavailable(isBackendDown(error));
    },
  }));
};
