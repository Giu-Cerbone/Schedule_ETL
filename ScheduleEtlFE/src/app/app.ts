import { Component, OnInit, inject, signal } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { ToastModule } from 'primeng/toast';
import { filter, firstValueFrom } from 'rxjs';
import { ScheduleApiService } from './service/schedule-api.service';
import { ChangeNotifier } from './service/change-notifier.service';
import { BackendStatusService } from './service/backend-status.service';
import { NotificationService } from './service/notification.service';
import { WorkspaceService } from './service/workspace.service';
import { markOnChanges } from './service/change-notifier.service';
import { MENU_ROUTES } from './app.routes';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, ButtonModule, DialogModule, ToastModule],
  templateUrl: './app.html',
})
export class App implements OnInit {
  readonly workspace = inject(WorkspaceService);
  readonly notifications = inject(NotificationService);
  readonly backend = inject(BackendStatusService);
  private readonly api = inject(ScheduleApiService);
  private readonly notifier = inject(ChangeNotifier);
  ready = false;
  private readonly router = inject(Router);
  readonly menu = MENU_ROUTES;
  readonly title = signal('Dashboard');

  constructor() {
    markOnChanges();
    this.router.events.pipe(filter((event) => event instanceof NavigationEnd)).subscribe((event) => {
      const path = (event as NavigationEnd).urlAfterRedirects.split(/[?#]/)[0].replace(/^\//, '');
      this.title.set(this.menu.find((item) => item.path === path)?.label ?? 'Dashboard');
    });
  }

  ngOnInit(): void {
    void this.startBackendCheck();
  }

  /** L'applicazione (e quindi la dashboard) parte solo se il backend risponde. */
  async startBackendCheck(): Promise<void> {
    this.backend.retrying = true;
    this.notifier.notify();
    try {
      await firstValueFrom(this.api.listSchedulers());
      this.ready = true;
      this.backend.setUnavailable(false);
      this.backend.retrying = false;
      this.notifier.notify();
      await this.workspace.refresh();
    } catch {
      this.backend.setUnavailable(true);
    } finally {
      this.backend.retrying = false;
      this.notifier.notify();
    }
  }

  async retryBackend(): Promise<void> {
    if (this.ready) {
      this.backend.retrying = true;
      await this.workspace.refresh();
      this.backend.retrying = false;
    } else {
      await this.startBackendCheck();
    }
  }

  count(path: string): number | null {
    if (path === 'schedulers') return this.workspace.schedulers.length;
    if (path === 'shell-commands') return this.workspace.shellCommands.length + this.workspace.shellScripts.length;
    return null;
  }
}
