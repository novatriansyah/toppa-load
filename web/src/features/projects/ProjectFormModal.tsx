import React, { useEffect, useState, useRef } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { apiClient, HttpError } from '../../shared/api/httpClient';
import {
  ProjectSummary,
  CreateProjectRequest,
  UpdateProjectRequest,
} from './ProjectTypes';
import { UserSummary } from '../settings/UsersSettingsSection';

interface ProjectFormModalProps {
  isOpen: boolean;
  onClose: () => void;
  project?: ProjectSummary | null;
}

export const ProjectFormModal: React.FC<ProjectFormModalProps> = ({
  isOpen,
  onClose,
  project,
}) => {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const firstInputRef = useRef<HTMLInputElement>(null);

  const [code, setCode] = useState('');
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [status, setStatus] = useState('ACTIVE');
  const [startDate, setStartDate] = useState('');
  const [endDate, setEndDate] = useState('');
  const [ownerId, setOwnerId] = useState('');
  const [formError, setFormError] = useState<string | null>(null);

  // Fetch users for owner select
  const { data: users } = useQuery<UserSummary[]>({
    queryKey: ['users'],
    queryFn: () => apiClient.get<UserSummary[]>('/api/v1/users'),
    enabled: isOpen,
  });

  useEffect(() => {
    if (isOpen) {
      if (project) {
        setCode(project.code || '');
        setName(project.name || '');
        setDescription(project.description || '');
        setStatus(project.status || 'ACTIVE');
        setStartDate(project.startDate || '');
        setEndDate(project.endDate || '');
        setOwnerId(project.ownerId || '');
      } else {
        setCode('');
        setName('');
        setDescription('');
        setStatus('ACTIVE');
        const today = new Date().toISOString().split('T')[0];
        setStartDate(today);
        setEndDate(today);
        setOwnerId('');
      }
      setFormError(null);
      setTimeout(() => firstInputRef.current?.focus(), 50);
    }
  }, [isOpen, project]);

  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && isOpen) {
        onClose();
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [isOpen, onClose]);

  const createMutation = useMutation({
    mutationFn: (payload: CreateProjectRequest) =>
      apiClient.post<ProjectSummary>('/api/v1/projects', payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['projects'] });
      onClose();
    },
    onError: (err: unknown) => {
      if (err instanceof HttpError) {
        setFormError(err.message);
      } else {
        setFormError(t('common.error'));
      }
    },
  });

  const updateMutation = useMutation({
    mutationFn: ({ id, payload }: { id: string; payload: UpdateProjectRequest }) =>
      apiClient.put<ProjectSummary>(`/api/v1/projects/${id}`, payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['projects'] });
      onClose();
    },
    onError: (err: unknown) => {
      if (err instanceof HttpError) {
        setFormError(err.message);
      } else {
        setFormError(t('common.error'));
      }
    },
  });

  if (!isOpen) return null;

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setFormError(null);

    const trimmedCode = code.trim().toUpperCase();
    const trimmedName = name.trim();

    if (!trimmedCode) {
      setFormError(t('projects.form.codeRequired'));
      return;
    }
    if (!trimmedName) {
      setFormError(t('projects.form.nameRequired'));
      return;
    }
    if (!startDate || !endDate) {
      setFormError(t('projects.form.dateRequired'));
      return;
    }
    if (endDate < startDate) {
      setFormError(t('projects.form.dateRangeInvalid'));
      return;
    }

    if (project) {
      updateMutation.mutate({
        id: project.id,
        payload: {
          code: trimmedCode,
          name: trimmedName,
          description: description.trim() || null,
          status,
          startDate,
          endDate,
          ownerId: ownerId || null,
        },
      });
    } else {
      createMutation.mutate({
        code: trimmedCode,
        name: trimmedName,
        description: description.trim() || null,
        status,
        startDate,
        endDate,
        ownerId: ownerId || null,
      });
    }
  };

  const isPending = createMutation.isPending || updateMutation.isPending;

  return (
    <div className="fixed inset-0 z-50 bg-slate-900/40 backdrop-blur-xs flex items-center justify-center p-4">
      <div
        className="w-full max-w-lg bg-white border border-slate-200 rounded-md shadow-lg p-5 space-y-4"
        role="dialog"
        aria-modal="true"
        aria-labelledby="project-modal-title"
      >
        {/* Modal Header */}
        <div className="flex items-center justify-between pb-2 border-b border-slate-200">
          <h2 id="project-modal-title" className="text-sm font-semibold text-slate-900">
            {project ? t('projects.form.editTitle') : t('projects.form.createTitle')}
          </h2>
          <button
            type="button"
            onClick={onClose}
            className="text-slate-400 hover:text-slate-600 p-1 rounded"
            aria-label={t('common.close')}
          >
            ✕
          </button>
        </div>

        {formError && (
          <div className="p-2.5 rounded bg-rose-50 border border-rose-200 text-xs text-rose-700">
            {formError}
          </div>
        )}

        <form onSubmit={handleSubmit} className="space-y-3 text-xs">
          {/* Code & Status Row */}
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block font-medium text-slate-700 mb-1">
                {t('projects.form.code')} <span className="text-rose-500">*</span>
              </label>
              <input
                ref={firstInputRef}
                type="text"
                maxLength={24}
                value={code}
                onChange={(e) => setCode(e.target.value.toUpperCase())}
                placeholder="PRJ-101"
                required
                className="w-full px-2.5 py-1.5 rounded border border-slate-200 focus:border-slate-400 focus:outline-none uppercase font-mono"
              />
            </div>
            <div>
              <label className="block font-medium text-slate-700 mb-1">
                {t('projects.form.status')}
              </label>
              <select
                value={status}
                onChange={(e) => setStatus(e.target.value)}
                className="w-full px-2.5 py-1.5 rounded border border-slate-200 focus:border-slate-400 focus:outline-none bg-white"
              >
                <option value="ACTIVE">{t('projects.status.ACTIVE')}</option>
                <option value="PLANNING">{t('projects.status.PLANNING')}</option>
                <option value="COMPLETED">{t('projects.status.COMPLETED')}</option>
                <option value="ARCHIVED">{t('projects.status.ARCHIVED')}</option>
              </select>
            </div>
          </div>

          {/* Name */}
          <div>
            <label className="block font-medium text-slate-700 mb-1">
              {t('projects.form.name')} <span className="text-rose-500">*</span>
            </label>
            <input
              type="text"
              maxLength={160}
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="e.g. Migrasi Core Database"
              required
              className="w-full px-2.5 py-1.5 rounded border border-slate-200 focus:border-slate-400 focus:outline-none"
            />
          </div>

          {/* Dates Row */}
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block font-medium text-slate-700 mb-1">
                {t('projects.form.startDate')} <span className="text-rose-500">*</span>
              </label>
              <input
                type="date"
                value={startDate}
                onChange={(e) => setStartDate(e.target.value)}
                required
                className="w-full px-2.5 py-1.5 rounded border border-slate-200 focus:border-slate-400 focus:outline-none num-tabular"
              />
            </div>
            <div>
              <label className="block font-medium text-slate-700 mb-1">
                {t('projects.form.endDate')} <span className="text-rose-500">*</span>
              </label>
              <input
                type="date"
                value={endDate}
                onChange={(e) => setEndDate(e.target.value)}
                required
                className="w-full px-2.5 py-1.5 rounded border border-slate-200 focus:border-slate-400 focus:outline-none num-tabular"
              />
            </div>
          </div>

          {/* Owner */}
          <div>
            <label className="block font-medium text-slate-700 mb-1">
              {t('projects.form.owner')}
            </label>
            <select
              value={ownerId}
              onChange={(e) => setOwnerId(e.target.value)}
              className="w-full px-2.5 py-1.5 rounded border border-slate-200 focus:border-slate-400 focus:outline-none bg-white"
            >
              <option value="">{t('projects.form.noOwner')}</option>
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
              {t('projects.form.description')}
            </label>
            <textarea
              rows={3}
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="Catatan ruang lingkup proyek..."
              className="w-full px-2.5 py-1.5 rounded border border-slate-200 focus:border-slate-400 focus:outline-none resize-none"
            />
          </div>

          {/* Footer Actions */}
          <div className="flex items-center justify-end space-x-2 pt-3 border-t border-slate-100">
            <button
              type="button"
              onClick={onClose}
              className="px-3 py-1.5 rounded bg-slate-100 text-slate-700 font-medium hover:bg-slate-200 transition-colors"
            >
              {t('common.cancel')}
            </button>
            <button
              type="submit"
              disabled={isPending}
              className="px-3 py-1.5 rounded bg-slate-900 text-white font-medium hover:bg-slate-800 transition-colors disabled:opacity-50"
            >
              {isPending ? t('app.loading') : t('common.save')}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

export default ProjectFormModal;
