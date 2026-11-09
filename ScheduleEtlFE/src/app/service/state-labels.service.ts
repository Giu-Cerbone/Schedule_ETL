import { Injectable } from '@angular/core';

export type StateSeverity = 'success' | 'info' | 'warn' | 'danger' | 'secondary';

@Injectable({ providedIn: 'root' })
export class StateLabelsService {
  stateLabel(state: string): string {
    const labels: Record<string, string> = {
      ACTIVE: 'Attivo', INACTIVE: 'Inattivo', PAUSED: 'In pausa', DELETED: 'Eliminato',
      CREATED: 'Creato', QUEUED: 'In coda', RUNNING: 'In esecuzione', STOPPING: 'Arresto',
      STOPPED: 'Fermato', COMPLETED: 'Completato', FAILED: 'Fallito (fermato)', CANCELLED: 'Annullato',
    };
    return labels[state] ?? state;
  }

  stateSeverity(state: string): StateSeverity {
    if (['ACTIVE', 'RUNNING', 'COMPLETED'].includes(state)) return 'success';
    if (['PAUSED', 'QUEUED', 'STOPPING'].includes(state)) return 'warn';
    if (['FAILED', 'DELETED'].includes(state)) return 'danger';
    if (['INACTIVE', 'STOPPED', 'CANCELLED'].includes(state)) return 'secondary';
    return 'info';
  }

  isExecutionActive(state: string): boolean {
    return ['QUEUED', 'RUNNING', 'PAUSED', 'STOPPING'].includes(state);
  }
}
