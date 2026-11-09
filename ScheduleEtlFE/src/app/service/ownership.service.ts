import { Injectable, inject } from '@angular/core';
import { Observable, firstValueFrom } from 'rxjs';
import { CreateGroup, CreateProfile, UserGroup, UserProfile } from './api.models';
import { NotificationService } from './notification.service';
import { ScheduleApiService } from './schedule-api.service';
import { WorkspaceService } from './workspace.service';

@Injectable({ providedIn: 'root' })
export class OwnershipService {
  private readonly api = inject(ScheduleApiService);
  private readonly workspace = inject(WorkspaceService);
  private readonly notifications = inject(NotificationService);

  async createProfile(form: CreateProfile): Promise<void> {
    await firstValueFrom(this.api.createProfile(form));
    await this.afterSave();
  }

  async createGroup(form: CreateGroup): Promise<void> {
    await firstValueFrom(this.api.createGroup(form));
    await this.afterSave();
  }

  profileDetails(profile: UserProfile): Observable<unknown> {
    return this.api.getProfile(profile.id);
  }

  groupDetails(group: UserGroup): Observable<unknown> {
    return this.api.getGroup(group.id);
  }

  private async afterSave(): Promise<void> {
    this.notifications.success('Salvato', 'Le modifiche sono state salvate.');
    await this.workspace.refresh();
  }
}
