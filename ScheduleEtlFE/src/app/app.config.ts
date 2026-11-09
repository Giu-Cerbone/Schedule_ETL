import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideRouter } from '@angular/router';

import { MessageService } from 'primeng/api';
import { adminAuthInterceptor } from './service/admin-auth.interceptor';
import { backendStatusInterceptor } from './service/backend-status.interceptor';
import { routes } from './app.routes';

export const appConfig: ApplicationConfig = {
  providers: [
    MessageService,
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    provideHttpClient(withInterceptors([adminAuthInterceptor, backendStatusInterceptor])),
  ]
};
