import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { TableModule } from 'primeng/table';
import { TextareaModule } from 'primeng/textarea';
import { CreateGroup, CreateProfile, UserGroup, UserProfile } from '../../service/api.models';
import { markOnChanges } from '../../service/change-notifier.service';
import { NotificationService } from '../../service/notification.service';
import { OwnershipService } from '../../service/ownership.service';
import { WorkspaceService } from '../../service/workspace.service';
import { DetailsDialogComponent } from '../details-dialog/details-dialog.component';

@Component({
  selector: 'app-ownership',
  standalone: true,
  imports: [FormsModule, ButtonModule, DialogModule, InputTextModule, TableModule, TextareaModule, DetailsDialogComponent],
  templateUrl: './ownership.component.html',
})
export class OwnershipComponent {
  readonly workspace = inject(WorkspaceService);
  readonly ownership = inject(OwnershipService);
  private readonly notifications = inject(NotificationService);

  saving = false;
  profileOpen = false;
  groupOpen = false;
  detailsOpen = false;
  detailsTitle = '';
  detailsText = '';
  profileForm: CreateProfile = { username: '', fullName: '', email: '' };
  groupForm: CreateGroup = { name: '', description: '' };

  constructor() {
    markOnChanges();
  }

  trackById(_index: number, item: { id: number }): number {
    return item.id;
  }

  openProfile(): void {
    this.profileForm = { username: '', fullName: '', email: '' };
    this.profileOpen = true;
  }

  openGroup(): void {
    this.groupForm = { name: '', description: '' };
    this.groupOpen = true;
  }

  async saveProfile(): Promise<void> {
    await this.save(() => this.ownership.createProfile(this.profileForm), () => (this.profileOpen = false));
  }

  async saveGroup(): Promise<void> {
    await this.save(() => this.ownership.createGroup(this.groupForm), () => (this.groupOpen = false));
  }

  async showProfileDetails(profile: UserProfile): Promise<void> {
    await this.showDetails(`Profilo · ${profile.fullName}`, this.ownership.profileDetails(profile));
  }

  async showGroupDetails(group: UserGroup): Promise<void> {
    await this.showDetails(`Gruppo · ${group.name}`, this.ownership.groupDetails(group));
  }

  private async showDetails(title: string, request: ReturnType<OwnershipService['profileDetails']>): Promise<void> {
    try {
      const details: unknown = await firstValueFrom(request);
      this.detailsTitle = title;
      this.detailsText = JSON.stringify(details, null, 2);
      this.detailsOpen = true;
    } catch (error) {
      this.notifications.reportError(error);
    }
  }

  private async save(action: () => Promise<void>, close: () => void): Promise<void> {
    this.saving = true;
    try {
      await action();
      close();
    } catch (error) {
      this.notifications.reportError(error);
    } finally {
      this.saving = false;
    }
  }
}
