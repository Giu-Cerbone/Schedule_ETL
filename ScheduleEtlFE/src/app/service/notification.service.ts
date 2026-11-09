import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { MessageService } from 'primeng/api';
import { ChangeNotifier } from './change-notifier.service';

@Injectable({ providedIn: 'root' })
export class NotificationService {
  private readonly messages = inject(MessageService);
  private readonly notifier = inject(ChangeNotifier);
  error = '';

  success(summary: string, detail: string): void {
    this.messages.add({ severity: 'success', summary, detail });
  }

  clearError(): void {
    this.error = '';
    this.notifier.notify();
  }

  reportError(error: unknown): void {
    let message: string;
    if (error instanceof HttpErrorResponse) {
      if (error.status === 0) {
        message = 'Backend non raggiungibile. Verifica che Spring Boot sia avviato su http://localhost:8080 e che il proxy frontend sia attivo.';
      } else if (error.status === 401) {
        message = 'Autenticazione richiesta o credenziali non valide. Per l’accesso admin configura SCHEDULETL_ADMIN_PASSWORD e riavvia il backend.';
      } else if (error.status === 403) {
        message = 'Operazione non autorizzata. Accedi con un account amministratore.';
      } else {
        const responseMessage = this.errorResponseMessage(error.error);
        message = responseMessage
          ? `Errore backend (${error.status}): ${responseMessage}`
          : `Errore backend (${error.status} ${error.statusText || 'HTTP'}).`;
      }
    } else {
      message = error instanceof Error ? error.message : 'Errore durante la comunicazione con il backend.';
    }
    this.error = message;
    this.messages.add({ severity: 'error', summary: 'Operazione non riuscita', detail: message, life: 6000 });
    this.notifier.notify();
  }

  private errorResponseMessage(response: unknown): string {
    if (typeof response === 'string') return response;
    if (typeof response === 'object' && response !== null && 'message' in response
      && typeof response.message === 'string') {
      return response.message;
    }
    if (typeof response === 'object' && response !== null && 'detail' in response
      && typeof response.detail === 'string') {
      return response.detail;
    }
    return '';
  }
}
