import React, { useState, useRef, useEffect } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { apiClient, HttpError } from '../../shared/api/httpClient';
import {
  TaskSummary,
  TaskStatus,
  TaskPriority,
  CreateTaskRequest,
  UpdateTaskRequest,
} from './ProjectTypes';
import { UserSummary } from '../settings/UsersSettingsSection';

export interface ProjectTasksDrawerProps {
  project: {
    id: string;
    code: string;
    name: string;
    startDate?: string;
    endDate?: string;
  } | null;
  isOpen: boolean;
  onClose: () => void;
}

function formatDate(d?: string | null): string {
  if (!d) return '-';
  const parts = d.split('-');
  if (parts.length === 3) {
    const [y, m, day] = parts;
    return `${day.padStart(2, '0')}/${m.padStart(2, '0')}/${y}`;
  }
  return d;
}

function formatDateRange(start?: string | null, due?: string | null): string {
  if (!start && !due) return '-';
  if (start && due) return `${formatDate(start)} – ${formatDate(due)}`;
  if (start) return formatDate(start);
  return formatDate(due);
}

function formatHours(hours?: number | null): string {
  if (hours === undefined || hours === null) return '0j';
  const num = Number(hours);
  const formatted = num % 1 === 0 ? String(Math.round(num)) : num.toFixed(1);
  return `${formatted}j`;
}

export const ProjectTasksDrawer: React.FC<ProjectTasksDrawerProps> = ({
  project,
  isOpen,
  onClose,
}) => {
  const { t } = useTranslation();
  const queryClient = useQueryClient();

  const quickAddInputRef = useRef<HTMLInputElement>(null);
  const [quickTitle, setQuickTitle] = useState('');
  const [searchQuery, setSearchQuery] = useState('');
  const [statusFilter, setStatusFilter] = useState('');

  // Edit task state
  const [editingTask, setEditingTask] = useState<TaskSummary | null>(null);
  const [editTitle, setEditTitle] = useState('');
  const [editDescription, setEditDescription] = useState('');
  const [editStatus, setEditStatus] = useState<TaskStatus>('TODO');
  const [editPriority, setEditPriority] = useState<TaskPriority>('MEDIUM');
  const [editHours, setEditHours] = useState('0');
  const [editProgress, setEditProgress] = useState('0');
  const [editStartDate, setEditStartDate] = useState('');
  const [editDueDate, setEditDueDate] = useState('');
  const [editAssigneeId, setEditAssigneeId] = useState('');
  const [editErrors, setEditErrors] = useState<{ hours?: string; progress?: string; general?: string }>({});

  // Keyboard shortcut: Escape to close drawer or modal
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && isOpen) {
        if (editingTask) {
          setEditingTask(null);
        } else {
          onClose();
        }
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [isOpen, editingTask, onClose]);

  // Fetch tasks for the project
  const { data: tasks, isLoading, error } = useQuery<TaskSummary[]>({
    queryKey: ['projectTasks', project?.id, searchQuery, statusFilter],
    queryFn: () => {
      if (!project?.id) return Promise.resolve([]);
      return apiClient.get<TaskSummary[]>(`/api/v1/projects/${project.id}/tasks`, {
        params: {
          q: searchQuery || undefined,
          status: statusFilter || undefined,
        },
      });
    },
    enabled: isOpen && !!project?.id,
  });

  // Fetch users for assignee selection
  const { data: users } = useQuery<UserSummary[]>({
    queryKey: ['users'],
    queryFn: () => apiClient.get<UserSummary[]>('/api/v1/users'),
    enabled: isOpen,
  });

  // Quick-Add Mutation
  const quickAddMutation = useMutation({
    mutationFn: (payload: CreateTaskRequest) => {
      if (!project?.id) throw new Error('No project selected');
      return apiClient.post<TaskSummary>(`/api/v1/projects/${project.id}/tasks`, payload);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['projectTasks', project?.id] });
      queryClient.invalidateQueries({ queryKey: ['projects'] });
      setQuickTitle('');
      // Crucial requirement: Maintain keyboard focus for rapid entry!
      setTimeout(() => {
        quickAddInputRef.current?.focus();
      }, 0);
    },
  });

  // Update Task Mutation
  const updateTaskMutation = useMutation({
    mutationFn: ({ id, payload }: { id: string; payload: UpdateTaskRequest }) =>
      apiClient.put<TaskSummary>(`/api/v1/tasks/${id}`, payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['projectTasks', project?.id] });
      queryClient.invalidateQueries({ queryKey: ['projects'] });
      setEditingTask(null);
    },
    onError: (err: unknown) => {
      if (err instanceof HttpError) {
        setEditErrors({ general: err.message });
      } else {
        setEditErrors({ general: t('common.error') });
      }
    },
  });

  // Delete Task Mutation
  const deleteTaskMutation = useMutation({
    mutationFn: (id: string) => apiClient.delete(`/api/v1/tasks/${id}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['projectTasks', project?.id] });
      queryClient.invalidateQueries({ queryKey: ['projects'] });
    },
  });

  if (!isOpen || !project) return null;

  const handleQuickAddKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Enter') {
      e.preventDefault();
      const trimmed = quickTitle.trim();
      if (!trimmed) return;

      quickAddMutation.mutate({
        title: trimmed,
        status: 'TODO',
        priority: 'MEDIUM',
        estimatedHours: 0,
        progressPercent: 0,
      });
    }
  };

  const handleOpenEdit = (task: TaskSummary) => {
    setEditingTask(task);
    setEditTitle(task.title);
    setEditDescription(task.description || '');
    setEditStatus((task.status as TaskStatus) || 'TODO');
    setEditPriority((task.priority as TaskPriority) || 'MEDIUM');
    setEditHours(String(task.estimatedHours ?? 0));
    setEditProgress(String(task.progressPercent ?? 0));
    setEditStartDate(task.startDate || '');
    setEditDueDate(task.dueDate || '');
    setEditAssigneeId(task.assigneeId || '');
    setEditErrors({});
  };

  const handleSaveEdit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!editingTask) return;

    const errors: { hours?: string; progress?: string; general?: string } = {};

    const hoursNum = parseFloat(editHours);
    if (isNaN(hoursNum) || hoursNum < 0) {
      errors.hours = t('projects.tasks.hoursMinError');
    }

    const progressNum = parseInt(editProgress, 10);
    if (isNaN(progressNum) || progressNum < 0 || progressNum > 100) {
      errors.progress = t('projects.tasks.progressRangeError');
    }

    if (editStartDate && editDueDate && editDueDate < editStartDate) {
      errors.general = t('projects.tasks.dueDateBeforeStartError');
    }

    if (Object.keys(errors).length > 0) {
      setEditErrors(errors);
      return;
    }

    updateTaskMutation.mutate({
      id: editingTask.id,
      payload: {
        title: editTitle.trim(),
        description: editDescription.trim() || null,
        status: editStatus,
        priority: editPriority,
        estimatedHours: hoursNum,
        progressPercent: progressNum,
        startDate: editStartDate || null,
        dueDate: editDueDate || null,
        assigneeId: editAssigneeId || null,
      },
    });
  };

  return (
    <div className="fixed inset-0 z-40 bg-slate-900/40 backdrop-blur-xs flex justify-end">
      {/* Slide-over Drawer Panel */}
      <div
        className="w-full max-w-3xl bg-white h-full shadow-2xl flex flex-col border-l border-slate-200"
        role="dialog"
        aria-modal="true"
        aria-label={t('projects.tasks.drawerTitle')}
      >
        {/* Drawer Header */}
        <div className="p-3.5 bg-slate-50 border-b border-slate-200 flex items-center justify-between select-none">
          <div className="flex items-center space-x-2.5">
            <span className="text-xs font-mono font-semibold px-2 py-0.5 rounded bg-slate-200 text-slate-800">
              {project.code}
            </span>
            <div>
              <h2 className="text-sm font-semibold text-slate-900 leading-tight">
                {project.name}
              </h2>
              {project.startDate && project.endDate && (
                <span className="text-[11px] text-slate-500 font-mono">
                  {formatDateRange(project.startDate, project.endDate)}
                </span>
              )}
            </div>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="p-1 rounded text-slate-400 hover:text-slate-600 hover:bg-slate-200/50 transition-colors"
            title={t('common.close')}
            aria-label={t('common.close')}
          >
            <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>

        {/* Quick-Add Bar (Keyboard First!) */}
        <div className="p-3 bg-white border-b border-slate-200">
          <div className="relative flex items-center">
            <div className="absolute left-2.5 text-slate-400 pointer-events-none">
              <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                <path strokeLinecap="round" strokeLinejoin="round" d="M12 4v16m8-8H4" />
              </svg>
            </div>
            <input
              ref={quickAddInputRef}
              type="text"
              value={quickTitle}
              onChange={(e) => setQuickTitle(e.target.value)}
              onKeyDown={handleQuickAddKeyDown}
              placeholder={t('projects.tasks.quickAddPlaceholder')}
              disabled={quickAddMutation.isPending}
              className="w-full pl-8 pr-16 py-2 text-xs rounded border border-slate-300 focus:border-slate-800 focus:ring-1 focus:ring-slate-800 focus:outline-none placeholder-slate-400 shadow-xs"
            />
            <div className="absolute right-2 text-[10px] text-slate-400 font-mono border border-slate-200 px-1 py-0.5 rounded bg-slate-50 select-none">
              Enter ↵
            </div>
          </div>
        </div>

        {/* Filter & Search Bar */}
        <div className="px-3 py-2 bg-slate-50/75 border-b border-slate-200 flex items-center justify-between gap-2 text-xs">
          <div className="flex-1 max-w-xs relative">
            <input
              type="text"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              placeholder={t('projects.tasks.searchPlaceholder')}
              className="w-full px-2 py-1 bg-white rounded border border-slate-200 text-xs focus:outline-none focus:border-slate-400"
            />
          </div>
          <select
            value={statusFilter}
            onChange={(e) => setStatusFilter(e.target.value)}
            className="px-2 py-1 bg-white rounded border border-slate-200 text-xs focus:outline-none focus:border-slate-400"
          >
            <option value="">{t('projects.tasks.allStatuses')}</option>
            <option value="TODO">{t('projects.tasks.statusTODO')}</option>
            <option value="IN_PROGRESS">{t('projects.tasks.statusIN_PROGRESS')}</option>
            <option value="DONE">{t('projects.tasks.statusDONE')}</option>
            <option value="BLOCKED">{t('projects.tasks.statusBLOCKED')}</option>
          </select>
        </div>

        {/* Tasks List */}
        <div className="flex-1 overflow-auto bg-slate-50">
          {isLoading ? (
            <div className="p-6 text-xs text-slate-500 text-center">{t('app.loading')}</div>
          ) : error ? (
            <div className="p-6 text-xs text-rose-600 text-center">{t('common.error')}</div>
          ) : !tasks || tasks.length === 0 ? (
            <div className="p-8 text-center text-xs text-slate-400 space-y-1">
              <p>{t('projects.tasks.empty')}</p>
              <p className="text-[11px] text-slate-400">{t('projects.tasks.emptyTip')}</p>
            </div>
          ) : (
            <table className="w-full text-left border-collapse text-xs">
              <thead>
                <tr className="bg-slate-100 border-b border-slate-200 text-slate-600 font-medium select-none">
                  <th className="py-2 px-3">{t('projects.tasks.colTitle')}</th>
                  <th className="py-2 px-2.5">{t('projects.tasks.colStatus')}</th>
                  <th className="py-2 px-2.5 text-right">{t('projects.tasks.colProgress')}</th>
                  <th className="py-2 px-2.5 text-right">{t('projects.tasks.colHours')}</th>
                  <th className="py-2 px-2.5 text-right">{t('projects.tasks.colDates')}</th>
                  <th className="py-2 px-2.5">{t('projects.tasks.colAssignee')}</th>
                  <th className="py-2 px-3 text-right">{t('common.actions')}</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-200 bg-white">
                {tasks.map((task) => (
                  <tr key={task.id} className="hover:bg-slate-50/75 transition-colors">
                    {/* Title & Priority */}
                    <td className="py-2.5 px-3">
                      <div className="font-medium text-slate-900 leading-tight">{task.title}</div>
                      {task.priority && task.priority !== 'MEDIUM' && (
                        <span
                          className={`inline-block mt-0.5 text-[10px] font-semibold px-1 rounded uppercase tracking-wider ${
                            task.priority === 'URGENT'
                              ? 'bg-rose-100 text-rose-700'
                              : task.priority === 'HIGH'
                              ? 'bg-amber-100 text-amber-800'
                              : 'bg-slate-100 text-slate-600'
                          }`}
                        >
                          {task.priority}
                        </span>
                      )}
                    </td>

                    {/* Status */}
                    <td className="py-2.5 px-2.5 whitespace-nowrap">
                      <span
                        className={`inline-block px-1.5 py-0.5 rounded text-[11px] font-medium border ${
                          task.status === 'DONE'
                            ? 'bg-emerald-50 text-emerald-700 border-emerald-200'
                            : task.status === 'IN_PROGRESS'
                            ? 'bg-blue-50 text-blue-700 border-blue-200'
                            : task.status === 'BLOCKED'
                            ? 'bg-rose-50 text-rose-700 border-rose-200'
                            : 'bg-slate-100 text-slate-700 border-slate-200'
                        }`}
                      >
                        {t(`projects.tasks.status${task.status}`)}
                      </span>
                    </td>

                    {/* Progress % */}
                    <td className="py-2.5 px-2.5 text-right whitespace-nowrap">
                      <span className="num-tabular font-medium text-slate-800">
                        {task.progressPercent}%
                      </span>
                    </td>

                    {/* Estimated Hours */}
                    <td className="py-2.5 px-2.5 text-right whitespace-nowrap">
                      <span className="num-tabular font-medium text-slate-800">
                        {formatHours(task.estimatedHours)}
                      </span>
                    </td>

                    {/* Dates Range */}
                    <td className="py-2.5 px-2.5 text-right whitespace-nowrap">
                      <span className="num-tabular text-[11px] text-slate-600">
                        {formatDateRange(task.startDate, task.dueDate)}
                      </span>
                    </td>

                    {/* Assignee */}
                    <td className="py-2.5 px-2.5 text-slate-700 whitespace-nowrap">
                      {task.assigneeName ? (
                        <span className="inline-flex items-center space-x-1">
                          <span className="w-4 h-4 rounded-full bg-slate-200 text-[9px] font-bold flex items-center justify-center text-slate-700">
                            {task.assigneeName.slice(0, 1)}
                          </span>
                          <span className="text-xs font-medium">{task.assigneeName}</span>
                        </span>
                      ) : (
                        <span className="text-slate-400 text-[11px]">-</span>
                      )}
                    </td>

                    {/* Actions */}
                    <td className="py-2.5 px-3 text-right whitespace-nowrap space-x-1">
                      <button
                        type="button"
                        onClick={() => handleOpenEdit(task)}
                        className="px-2 py-1 rounded bg-slate-100 hover:bg-slate-200 text-slate-700 font-medium text-xs transition-colors border border-slate-200"
                        title={t('common.edit')}
                        aria-label={t('common.edit')}
                      >
                        {t('common.edit')}
                      </button>
                      <button
                        type="button"
                        onClick={() => {
                          if (window.confirm(t('projects.tasks.deleteConfirm'))) {
                            deleteTaskMutation.mutate(task.id);
                          }
                        }}
                        className="px-1.5 py-1 rounded text-rose-600 hover:bg-rose-50 transition-colors text-xs"
                        title={t('common.delete')}
                        aria-label={t('common.delete')}
                      >
                        ✕
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>

        {/* Drawer Footer note */}
        <div className="p-2 border-t border-slate-200 bg-white text-[11px] text-slate-400 flex justify-between select-none">
          <span>{t('projects.tasks.keyboardTip')}</span>
          <span>{tasks ? `${tasks.length} task` : ''}</span>
        </div>
      </div>

      {/* Edit Task Modal */}
      {editingTask && (
        <div className="fixed inset-0 z-50 bg-slate-900/50 backdrop-blur-xs flex items-center justify-center p-4">
          <div
            className="w-full max-w-md bg-white border border-slate-200 rounded-md shadow-xl p-5 space-y-4"
            role="dialog"
            aria-modal="true"
            aria-labelledby="edit-task-title"
          >
            <div className="flex items-center justify-between pb-2 border-b border-slate-200">
              <h3 id="edit-task-title" className="text-sm font-semibold text-slate-900">
                {t('projects.tasks.editTitle')}
              </h3>
              <button
                type="button"
                onClick={() => setEditingTask(null)}
                className="text-slate-400 hover:text-slate-600 p-1 rounded"
                aria-label={t('common.close')}
              >
                ✕
              </button>
            </div>

            {editErrors.general && (
              <div className="p-2 rounded bg-rose-50 border border-rose-200 text-xs text-rose-700">
                {editErrors.general}
              </div>
            )}

            <form noValidate onSubmit={handleSaveEdit} className="space-y-3 text-xs">
              {/* Title */}
              <div>
                <label className="block font-medium text-slate-700 mb-1">
                  {t('projects.tasks.formTitle')}
                </label>
                <input
                  type="text"
                  required
                  value={editTitle}
                  onChange={(e) => setEditTitle(e.target.value)}
                  className="w-full px-2.5 py-1.5 rounded border border-slate-200 focus:border-slate-400 focus:outline-none"
                />
              </div>

              {/* Status & Priority */}
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block font-medium text-slate-700 mb-1">
                    {t('projects.tasks.formStatus')}
                  </label>
                  <select
                    value={editStatus}
                    onChange={(e) => setEditStatus(e.target.value as TaskStatus)}
                    className="w-full px-2.5 py-1.5 rounded border border-slate-200 focus:border-slate-400 focus:outline-none bg-white"
                  >
                    <option value="TODO">{t('projects.tasks.statusTODO')}</option>
                    <option value="IN_PROGRESS">{t('projects.tasks.statusIN_PROGRESS')}</option>
                    <option value="DONE">{t('projects.tasks.statusDONE')}</option>
                    <option value="BLOCKED">{t('projects.tasks.statusBLOCKED')}</option>
                  </select>
                </div>
                <div>
                  <label className="block font-medium text-slate-700 mb-1">
                    {t('projects.tasks.formPriority')}
                  </label>
                  <select
                    value={editPriority}
                    onChange={(e) => setEditPriority(e.target.value as TaskPriority)}
                    className="w-full px-2.5 py-1.5 rounded border border-slate-200 focus:border-slate-400 focus:outline-none bg-white"
                  >
                    <option value="LOW">{t('projects.tasks.priorityLOW')}</option>
                    <option value="MEDIUM">{t('projects.tasks.priorityMEDIUM')}</option>
                    <option value="HIGH">{t('projects.tasks.priorityHIGH')}</option>
                    <option value="URGENT">{t('projects.tasks.priorityURGENT')}</option>
                  </select>
                </div>
              </div>

              {/* Progress & Estimated Hours */}
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label htmlFor="task-progress-input" className="block font-medium text-slate-700 mb-1">
                    {t('projects.tasks.formProgress')} (%)
                  </label>
                  <input
                    id="task-progress-input"
                    type="number"
                    min="0"
                    max="100"
                    value={editProgress}
                    onChange={(e) => setEditProgress(e.target.value)}
                    className="w-full px-2.5 py-1.5 rounded border border-slate-200 focus:border-slate-400 focus:outline-none num-tabular"
                  />
                  {editErrors.progress && (
                    <span className="text-[11px] text-rose-600 block mt-0.5">{editErrors.progress}</span>
                  )}
                </div>
                <div>
                  <label htmlFor="task-hours-input" className="block font-medium text-slate-700 mb-1">
                    {t('projects.tasks.formHours')} ({t('common.hoursShort')})
                  </label>
                  <input
                    id="task-hours-input"
                    type="number"
                    step="0.5"
                    min="0"
                    value={editHours}
                    onChange={(e) => setEditHours(e.target.value)}
                    className="w-full px-2.5 py-1.5 rounded border border-slate-200 focus:border-slate-400 focus:outline-none num-tabular"
                  />
                  {editErrors.hours && (
                    <span className="text-[11px] text-rose-600 block mt-0.5">{editErrors.hours}</span>
                  )}
                </div>
              </div>

              {/* Start Date & Due Date */}
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block font-medium text-slate-700 mb-1">
                    {t('projects.tasks.formStartDate')}
                  </label>
                  <input
                    type="date"
                    value={editStartDate}
                    onChange={(e) => setEditStartDate(e.target.value)}
                    className="w-full px-2.5 py-1.5 rounded border border-slate-200 focus:border-slate-400 focus:outline-none num-tabular"
                  />
                </div>
                <div>
                  <label className="block font-medium text-slate-700 mb-1">
                    {t('projects.tasks.formDueDate')}
                  </label>
                  <input
                    type="date"
                    value={editDueDate}
                    onChange={(e) => setEditDueDate(e.target.value)}
                    className="w-full px-2.5 py-1.5 rounded border border-slate-200 focus:border-slate-400 focus:outline-none num-tabular"
                  />
                </div>
              </div>

              {/* Assignee */}
              <div>
                <label className="block font-medium text-slate-700 mb-1">
                  {t('projects.tasks.formAssignee')}
                </label>
                <select
                  value={editAssigneeId}
                  onChange={(e) => setEditAssigneeId(e.target.value)}
                  className="w-full px-2.5 py-1.5 rounded border border-slate-200 focus:border-slate-400 focus:outline-none bg-white"
                >
                  <option value="">{t('projects.tasks.noAssignee')}</option>
                  {users?.map((u) => (
                    <option key={u.id} value={u.id}>
                      {u.fullName} ({u.email})
                    </option>
                  ))}
                </select>
              </div>

              {/* Description */}
              <div>
                <label className="block font-medium text-slate-700 mb-1">
                  {t('projects.tasks.formDescription')}
                </label>
                <textarea
                  rows={2}
                  value={editDescription}
                  onChange={(e) => setEditDescription(e.target.value)}
                  className="w-full px-2.5 py-1.5 rounded border border-slate-200 focus:border-slate-400 focus:outline-none resize-none"
                />
              </div>

              {/* Actions */}
              <div className="flex items-center justify-end space-x-2 pt-3 border-t border-slate-100">
                <button
                  type="button"
                  onClick={() => setEditingTask(null)}
                  className="px-3 py-1.5 rounded bg-slate-100 text-slate-700 font-medium hover:bg-slate-200 transition-colors"
                >
                  {t('common.cancel')}
                </button>
                <button
                  type="submit"
                  disabled={updateTaskMutation.isPending}
                  className="px-3 py-1.5 rounded bg-slate-900 text-white font-medium hover:bg-slate-800 transition-colors disabled:opacity-50"
                >
                  {updateTaskMutation.isPending ? t('app.loading') : t('common.save')}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};

export default ProjectTasksDrawer;
