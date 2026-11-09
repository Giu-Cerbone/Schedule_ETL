import { DatePipe } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { DatePickerModule } from 'primeng/datepicker';
import { InputTextModule } from 'primeng/inputtext';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { markOnChanges } from '../../service/change-notifier.service';
import { ProcessLogService } from '../../service/process-log.service';
import { WorkspaceService } from '../../service/workspace.service';

@Component({
  selector: 'app-process-logs',
  standalone: true,
  imports: [DatePipe, FormsModule, ButtonModule, DatePickerModule, InputTextModule, TableModule, TagModule],
  templateUrl: './process-logs.component.html',
})
export class ProcessLogsComponent implements OnInit {
  readonly logs = inject(ProcessLogService);
  readonly workspace = inject(WorkspaceService);

  constructor() {
    markOnChanges();
  }

  ngOnInit(): void {
    void this.logs.load();
  }

  trackById(_index: number, item: { id: number }): number {
    return item.id;
  }
}
