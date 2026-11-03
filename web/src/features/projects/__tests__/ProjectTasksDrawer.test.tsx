import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, beforeEach, vi, afterEach } from 'vitest';
import React from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ProjectTasksDrawer } from '../ProjectTasksDrawer';
import i18n from '../../../i18n';

const mockTasks = [
  {
    id: 'task-1',
    projectId: 'proj-1',
    projectCode: 'PRJ-ALPHA',
    projectName: 'Alpha Project',
    title: 'Setup Database Migration',
    description: 'Flyway migration setup',
    status: 'IN_PROGRESS',
    priority: 'HIGH',
    estimatedHours: 16.0,
    startDate: '2026-11-01',
    dueDate: '2026-11-05',
    progressPercent: 45,
    assigneeId: 'user-1',
    assigneeName: 'Budi Santoso',
    labels: ['backend'],
    createdAt: '2026-11-01T08:00:00Z',
    updatedAt: '2026-11-01T08:00:00Z',
  },
  {
    id: 'task-2',
    projectId: 'proj-1',
    projectCode: 'PRJ-ALPHA',
    projectName: 'Alpha Project',
    title: 'Design System Tokens',
    description: 'Work-tool CSS tokens',
    status: 'TODO',
    priority: 'MEDIUM',
    estimatedHours: 8.0,
    startDate: '2026-11-06',
    dueDate: '2026-11-10',
    progressPercent: 0,
    assigneeId: 'user-2',
    assigneeName: 'Siti Rahma',
    labels: ['design'],
    createdAt: '2026-11-01T09:00:00Z',
    updatedAt: '2026-11-01T09:00:00Z',
  },
];

const mockProject = {
  id: 'proj-1',
  code: 'PRJ-ALPHA',
  name: 'Alpha Project',
  startDate: '2026-11-01',
  endDate: '2026-11-30',
};

function renderComponent(props: Partial<React.ComponentProps<typeof ProjectTasksDrawer>> = {}) {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });

  return render(
    <QueryClientProvider client={queryClient}>
      <ProjectTasksDrawer
        project={mockProject}
        isOpen={true}
        onClose={vi.fn()}
        {...props}
      />
    </QueryClientProvider>
  );
}

describe('ProjectTasksDrawer', () => {
  beforeEach(async () => {
    await i18n.changeLanguage('id');
    vi.stubGlobal('fetch', vi.fn());
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('renders task list with title, status, progress %, estimated hours with num-tabular, date range, and assignee', async () => {
    (fetch as unknown as ReturnType<typeof vi.fn>).mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => mockTasks,
    });

    renderComponent();

    // Verify task title is rendered
    expect(await screen.findByText('Setup Database Migration')).toBeInTheDocument();
    expect(screen.getByText('Design System Tokens')).toBeInTheDocument();

    // Verify assignee names
    expect(screen.getByText('Budi Santoso')).toBeInTheDocument();
    expect(screen.getByText('Siti Rahma')).toBeInTheDocument();

    // Verify progress % rendered with num-tabular class
    const progressEl = screen.getByText('45%');
    expect(progressEl).toBeInTheDocument();
    expect(progressEl).toHaveClass('num-tabular');

    // Verify estimated hours rendered with num-tabular and 'j' unit
    const hoursEl = screen.getByText('16j');
    expect(hoursEl).toBeInTheDocument();
    expect(hoursEl).toHaveClass('num-tabular');

    const hoursEl2 = screen.getByText('8j');
    expect(hoursEl2).toBeInTheDocument();
    expect(hoursEl2).toHaveClass('num-tabular');

    // Verify dates rendered with num-tabular
    const dateEl = screen.getByText('01/11/2026 – 05/11/2026');
    expect(dateEl).toBeInTheDocument();
    expect(dateEl).toHaveClass('num-tabular');
  });

  it('quick-add row creates task on Enter, clears input, and maintains keyboard focus', async () => {
    (fetch as unknown as ReturnType<typeof vi.fn>).mockImplementation(async (url: string, init?: RequestInit) => {
      const urlStr = String(url);
      if (urlStr.includes('/tasks') && init?.method === 'POST') {
        const body = JSON.parse(init.body as string);
        return {
          ok: true,
          status: 201,
          json: async () => ({
            id: 'task-new',
            projectId: 'proj-1',
            projectCode: 'PRJ-ALPHA',
            projectName: 'Alpha Project',
            title: body.title,
            status: 'TODO',
            priority: 'MEDIUM',
            estimatedHours: 0,
            progressPercent: 0,
            createdAt: '2026-11-01T10:00:00Z',
          }),
        };
      }
      return {
        ok: true,
        status: 200,
        json: async () => mockTasks,
      };
    });

    renderComponent();

    const quickAddInput = (await screen.findByPlaceholderText(/tambah task cepat/i)) as HTMLInputElement;
    expect(quickAddInput).toBeInTheDocument();

    // Focus input, change value, and press Enter
    quickAddInput.focus();
    expect(quickAddInput).toHaveFocus();

    fireEvent.change(quickAddInput, { target: { value: 'Integrasi OAuth Google' } });
    fireEvent.keyDown(quickAddInput, { key: 'Enter', code: 'Enter' });

    // Verify API called with POST
    await waitFor(() => {
      const postCalls = (fetch as unknown as ReturnType<typeof vi.fn>).mock.calls.filter(
        (call: unknown[]) => {
          const [url, init] = call as [string, RequestInit];
          return String(url).includes('/api/v1/projects/proj-1/tasks') && init?.method === 'POST';
        }
      );
      expect(postCalls.length).toBe(1);
      const postBody = JSON.parse((postCalls[0][1] as RequestInit).body as string);
      expect(postBody.title).toBe('Integrasi OAuth Google');
    });

    // Input should be cleared
    expect(quickAddInput.value).toBe('');

    // Keyboard focus MUST remain on the quick-add input for rapid entry!
    expect(quickAddInput).toHaveFocus();
  });

  it('validates estimated hours (>= 0) and progress percent (0..100)', async () => {
    (fetch as unknown as ReturnType<typeof vi.fn>).mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => mockTasks,
    });

    renderComponent();

    // Click on Edit task button for the first task
    const editButtons = await screen.findAllByRole('button', { name: /ubah|edit/i });
    expect(editButtons.length).toBeGreaterThan(0);
    fireEvent.click(editButtons[0]);

    // Check modal or edit form opened with progress and hours inputs
    const progressInput = screen.getByLabelText(/progress/i);
    const hoursInput = screen.getByLabelText(/estimasi jam|workload/i);

    // Negative hours
    fireEvent.change(hoursInput, { target: { value: '-5' } });

    // Progress > 100
    fireEvent.change(progressInput, { target: { value: '150' } });

    // Click submit
    const saveButton = screen.getByRole('button', { name: /simpan|save/i });
    fireEvent.click(saveButton);

    // Validation errors should be visible
    expect(await screen.findByText(/antara 0 dan 100|0.*100/i)).toBeInTheDocument();
    expect(screen.getByText(/tidak boleh negatif|minimal 0|>= 0/i)).toBeInTheDocument();
  });
});
