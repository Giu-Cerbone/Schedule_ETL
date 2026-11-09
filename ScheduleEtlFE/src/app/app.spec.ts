import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { throwError } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
import { App } from './app';
import { routes } from './app.routes';
import { ScheduleApiService } from './service/schedule-api.service';
import { BackendStatusService } from './service/backend-status.service';
import { apiProviders, newApiMockState } from './service/testing/api-mocks';

describe('App', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [...apiProviders(newApiMockState()), provideRouter(routes)],
    }).compileComponents();
  });

  it('renders the shell with a link for every menu entry once the backend answers', async () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    const element = fixture.nativeElement as HTMLElement;

    expect(element.querySelector('.brand')?.textContent).toContain('SchedulETL');
    expect(element.querySelectorAll('.main-nav a')).toHaveLength(fixture.componentInstance.menu.length);
    fixture.destroy();
  });

  it('does not start the application while the backend is unreachable', async () => {
    const api = TestBed.inject(ScheduleApiService);
    api.listSchedulers = () => throwError(() => new HttpErrorResponse({ status: 0 }));
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(fixture.componentInstance.ready).toBe(false);
    expect(TestBed.inject(BackendStatusService).unavailable).toBe(true);
    expect((fixture.nativeElement as HTMLElement).querySelector('.app-shell')).toBeNull();
    expect(document.body.textContent).toContain('Backend non raggiungibile');
    fixture.destroy();
  });
});
