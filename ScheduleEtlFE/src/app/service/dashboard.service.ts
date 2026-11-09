import { Injectable, inject } from '@angular/core';
import { WorkspaceService } from './workspace.service';

@Injectable({ providedIn: 'root' })
export class DashboardService {
  private readonly workspace = inject(WorkspaceService);

  readonly stateChartOptions = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: { legend: { position: 'bottom' as const, labels: { usePointStyle: true, padding: 18 } } },
    cutout: '72%',
  };
  readonly activityChartOptions = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: { legend: { display: false } },
    scales: { x: { grid: { display: false } }, y: { beginAtZero: true, grid: { color: '#edf0f5' } } },
  };

  get enabledProcesses(): number {
    return this.workspace.processes.filter((process) => process.enabled).length;
  }

  get activeSchedulerCount(): number {
    return this.workspace.schedulers.filter((scheduler) => scheduler.status === 'ACTIVE').length;
  }

  get runningProcesses(): number {
    return this.workspace.processes.filter((process) => this.workspace.latestState(process.id) === 'RUNNING').length;
  }

  get stateChartData(): object {
    const counts = ['RUNNING', 'PAUSED', 'COMPLETED', 'FAILED', 'STOPPED'].map(
      (state) => this.workspace.processes.filter((process) => this.workspace.latestState(process.id) === state).length,
    );
    return {
      labels: ['In esecuzione', 'In pausa', 'Completati', 'Falliti', 'Fermati'],
      datasets: [{ data: counts, backgroundColor: ['#536dfe', '#f5a623', '#25b887', '#ed6575', '#c7ced9'], borderWidth: 0 }],
    };
  }

  get activityChartData(): object {
    const days = Array.from({ length: 7 }, (_, index) => {
      const date = new Date();
      date.setDate(date.getDate() - 6 + index);
      return date;
    });
    return {
      labels: days.map((date) => date.toLocaleDateString('it-IT', { weekday: 'short' })),
      datasets: [{
        label: 'Esecuzioni',
        data: days.map((date) => this.workspace.executions.filter(
          (execution) => execution.startedAt && new Date(execution.startedAt).toDateString() === date.toDateString(),
        ).length),
        borderColor: '#536dfe',
        backgroundColor: 'rgba(83,109,254,.12)',
        fill: true,
        tension: 0.4,
        pointRadius: 3,
      }],
    };
  }
}
