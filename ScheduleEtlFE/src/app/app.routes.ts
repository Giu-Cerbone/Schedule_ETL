import { Routes } from '@angular/router';
import { DashboardComponent } from './component/dashboard/dashboard.component';
import { ExecutionHistoryComponent } from './component/execution-history/execution-history.component';
import { OwnershipComponent } from './component/ownership/ownership.component';
import { ProcessLogsComponent } from './component/process-logs/process-logs.component';
import { ProcessComponent } from './component/process/process.component';
import { SchedulerComponent } from './component/scheduler/scheduler.component';
import { ShellCommandsComponent } from './component/shell-commands/shell-commands.component';

export interface MenuRoute {
  path: string;
  label: string;
  icon: string;
}

/** Voci del menu laterale: ogni voce corrisponde a un componente in src/component. */
export const MENU_ROUTES: MenuRoute[] = [
  { path: 'dashboard', label: 'Dashboard', icon: 'pi pi-th-large' },
  { path: 'schedulers', label: 'Scheduler', icon: 'pi pi-calendar' },
  { path: 'processes', label: 'Processi', icon: 'pi pi-sitemap' },
  { path: 'shell-commands', label: 'Comandi autorizzati', icon: 'pi pi-terminal' },
  { path: 'process-logs', label: 'Log processi', icon: 'pi pi-list' },
  { path: 'ownership', label: 'Utenti e gruppi', icon: 'pi pi-users' },
  { path: 'history', label: 'Esecuzioni', icon: 'pi pi-history' },
];

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
  { path: 'dashboard', component: DashboardComponent },
  { path: 'schedulers', component: SchedulerComponent },
  { path: 'processes', component: ProcessComponent },
  { path: 'shell-commands', component: ShellCommandsComponent },
  { path: 'process-logs', component: ProcessLogsComponent },
  { path: 'ownership', component: OwnershipComponent },
  { path: 'history', component: ExecutionHistoryComponent },
  { path: '**', redirectTo: 'dashboard' },
];
