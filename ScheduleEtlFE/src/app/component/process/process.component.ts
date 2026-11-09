import { DatePipe } from '@angular/common';
import { ChangeDetectorRef, Component, ElementRef, OnDestroy, OnInit, QueryList, ViewChildren, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { DatePickerModule } from 'primeng/datepicker';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { TextareaModule } from 'primeng/textarea';
import { TooltipModule } from 'primeng/tooltip';
import { ProcessDefinition } from '../../service/api.models';
import { markOnChanges } from '../../service/change-notifier.service';
import { toIsoDate } from '../../service/date-format';
import { ExecutionHistoryService } from '../../service/execution-history.service';
import { ProcessService } from '../../service/process.service';
import { StateLabelsService } from '../../service/state-labels.service';
import { WorkspaceService } from '../../service/workspace.service';

@Component({
  selector: 'app-process',
  standalone: true,
  imports: [
    DatePipe, FormsModule, ButtonModule, DatePickerModule, DialogModule, InputTextModule, SelectModule,
    TableModule, TagModule, TextareaModule, TooltipModule,
  ],
  templateUrl: './process.component.html',
})
export class ProcessComponent implements OnInit, OnDestroy {
  readonly workspace = inject(WorkspaceService);
  readonly processes = inject(ProcessService);
  readonly labels = inject(StateLabelsService);
  private readonly history = inject(ExecutionHistoryService);
  private readonly router = inject(Router);
  private readonly changeDetector = inject(ChangeDetectorRef);

  @ViewChildren('consoleOutput') private consoleOutputs!: QueryList<ElementRef<HTMLElement>>;
  dialogOpen = false;
  detailsOpen = false;
  readonly toIsoDate = toIsoDate;
  readonly shellModeOptions = [
    { label: 'Script approvato', value: 'script' },
    { label: 'Comando autorizzato', value: 'command' },
  ];

  constructor() {
    markOnChanges();
  }

  ngOnInit(): void {
    if (this.workspace.selectedSchedulerId !== null) void this.workspace.chooseScheduler(this.workspace.selectedSchedulerId);
    this.processes.startStatusPolling();
  }

  ngOnDestroy(): void {
    this.processes.stopStatusPolling();
    this.processes.closeDetails();
  }

  trackById(_index: number, item: { id: number }): number {
    return item.id;
  }

  openNew(): void {
    this.processes.newProcess();
    this.dialogOpen = true;
  }

  openChild(parent: ProcessDefinition): void {
    this.processes.newChildProcess(parent);
    this.dialogOpen = true;
  }

  openEdit(process: ProcessDefinition): void {
    this.processes.editProcess(process);
    this.dialogOpen = true;
  }

  async saveDialog(): Promise<void> {
    if (await this.processes.save()) this.dialogOpen = false;
  }

  async openDetails(process: ProcessDefinition): Promise<void> {
    this.detailsOpen = await this.processes.openDetails(process, () => this.scrollConsoles());
  }

  onDetailsVisibilityChange(visible: boolean): void {
    if (!visible) this.processes.closeDetails();
  }

  async openHistory(process: ProcessDefinition): Promise<void> {
    if (await this.history.load(process)) void this.router.navigate(['/history']);
  }

  private scrollConsoles(): void {
    this.changeDetector.detectChanges();
    for (const output of this.consoleOutputs) {
      output.nativeElement.scrollTop = output.nativeElement.scrollHeight;
    }
  }
}
