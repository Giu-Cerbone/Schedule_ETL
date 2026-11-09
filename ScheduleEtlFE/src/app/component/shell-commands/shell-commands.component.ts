import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { TextareaModule } from 'primeng/textarea';
import { markOnChanges } from '../../service/change-notifier.service';
import { ShellCatalogService } from '../../service/shell-catalog.service';
import { WorkspaceService } from '../../service/workspace.service';

@Component({
  selector: 'app-shell-commands',
  standalone: true,
  imports: [FormsModule, ButtonModule, DialogModule, InputTextModule, TableModule, TagModule, TextareaModule],
  templateUrl: './shell-commands.component.html',
})
export class ShellCommandsComponent implements OnInit {
  readonly workspace = inject(WorkspaceService);
  readonly catalog = inject(ShellCatalogService);

  constructor() {
    markOnChanges();
  }

  ngOnInit(): void {
    void this.catalog.refresh();
  }
}
