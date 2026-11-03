import React, { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { apiClient } from '../../shared/api/httpClient';
import { ProjectSummary } from './ProjectTypes';
import { ProjectFormModal } from './ProjectFormModal';
import { ProjectTasksDrawer } from './ProjectTasksDrawer';

function formatDate(d?: string | null): string {
  if (!d) return '-';
  const parts = d.split('-');
  if (parts.length === 3) {
    const [y, m, day] = parts;
    return `${day.padStart(2, '0')}/${m.padStart(2, '0')}/${y}`;
  }
  return d;
}

function formatDateRange(start?: string | null, end?: string | null): string {
  if (!start && !end) return '-';
  if (start && end) return `${formatDate(start)} – ${formatDate(end)}`;
  if (start) return formatDate(start);
  return formatDate(end);
}

export const ProjectsPage: React.FC = () => {
  const { t } = useTranslation();
  const queryClient = useQueryClient();

  const [searchQuery, setSearchQuery] = useState('');
  const [statusFilter, setStatusFilter] = useState('');
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingProject, setEditingProject] = useState<ProjectSummary | null>(null);

  // Selected project for Task Drawer
  const [selectedProjectForTasks, setSelectedProjectForTasks] = useState<ProjectSummary | null>(null);

  const { data: projects, isLoading, error } = useQuery<ProjectSummary[]>({
    queryKey: ['projects', searchQuery, statusFilter],
    queryFn: () =>
      apiClient.get<ProjectSummary[]>('/api/v1/projects', {
        params: {
          q: searchQuery || undefined,
          status: statusFilter || undefined,
        },
      }),
  });

  const deleteProjectMutation = useMutation({
    mutationFn: (id: string) => apiClient.delete(`/api/v1/projects/${id}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['projects'] });
      if (selectedProjectForTasks?.id) {
        setSelectedProjectForTasks(null);
      }
    },
  });

  const handleOpenCreate = () => {
    setEditingProject(null);
    setIsModalOpen(true);
  };

  const handleOpenEdit = (p: ProjectSummary) => {
    setEditingProject(p);
    setIsModalOpen(true);
  };

  const handleDelete = (p: ProjectSummary) => {
    if (window.confirm(t('projects.deleteConfirm', { name: p.name }))) {
      deleteProjectMutation.mutate(p.id);
    }
  };

  return (
    <div className="space-y-4 max-w-6xl mx-auto">
      {/* Top Header: Title & Actions */}
      <div className="pb-3 border-b border-slate-200 flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3">
        <div>
          <h1 className="text-base font-semibold text-slate-900">
            {t('projects.pageTitle')}
          </h1>
          <p className="text-xs text-slate-500">
            {t('projects.pageSubtitle')}
          </p>
        </div>

        <button
          type="button"
          onClick={handleOpenCreate}
          className="inline-flex items-center space-x-1.5 px-3 py-1.5 bg-slate-900 text-white rounded text-xs font-medium hover:bg-slate-800 transition-colors shadow-xs self-start sm:self-auto"
        >
          <svg className="w-3.5 h-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M12 4v16m8-8H4" />
          </svg>
          <span>{t('projects.createProject')}</span>
        </button>
      </div>

      {/* Filter and Search Bar */}
      <div className="flex flex-col sm:flex-row items-center justify-between gap-2 bg-white p-2.5 rounded border border-slate-200 text-xs">
        <div className="flex items-center space-x-2 w-full sm:w-auto">
          <div className="relative w-full sm:w-72">
            <input
              type="text"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              placeholder={t('projects.searchPlaceholder')}
              className="w-full pl-2.5 pr-2 py-1.5 rounded border border-slate-200 text-xs focus:outline-none focus:border-slate-400"
            />
          </div>
        </div>

        <div className="flex items-center space-x-2 w-full sm:w-auto justify-end">
          <span className="text-slate-500 font-medium text-[11px] select-none">
            {t('projects.filterStatus')}:
          </span>
          <select
            value={statusFilter}
            onChange={(e) => setStatusFilter(e.target.value)}
            className="px-2.5 py-1.5 rounded border border-slate-200 text-xs focus:outline-none focus:border-slate-400 bg-white"
          >
            <option value="">{t('projects.allStatuses')}</option>
            <option value="ACTIVE">{t('projects.status.ACTIVE')}</option>
            <option value="PLANNING">{t('projects.status.PLANNING')}</option>
            <option value="COMPLETED">{t('projects.status.COMPLETED')}</option>
            <option value="ARCHIVED">{t('projects.status.ARCHIVED')}</option>
          </select>
        </div>
      </div>

      {/* Projects Table */}
      <div className="bg-white border border-slate-200 rounded overflow-hidden shadow-xs">
        {isLoading ? (
          <div className="p-8 text-xs text-slate-500 text-center">{t('app.loading')}</div>
        ) : error ? (
          <div className="p-8 text-xs text-rose-600 text-center">{t('common.error')}</div>
        ) : !projects || projects.length === 0 ? (
          <div className="p-8 text-center text-xs text-slate-400 space-y-1">
            <p>{t('projects.empty')}</p>
            <p className="text-[11px] text-slate-400">{t('projects.emptyTip')}</p>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left border-collapse text-xs">
              <thead>
                <tr className="bg-slate-50 border-b border-slate-200 text-slate-600 font-medium select-none">
                  <th className="py-2.5 px-3 w-28">{t('projects.colCode')}</th>
                  <th className="py-2.5 px-3">{t('projects.colName')}</th>
                  <th className="py-2.5 px-3 w-28">{t('projects.colStatus')}</th>
                  <th className="py-2.5 px-3 w-48 text-right">{t('projects.colDates')}</th>
                  <th className="py-2.5 px-3 w-36">{t('projects.colOwner')}</th>
                  <th className="py-2.5 px-3 w-24 text-right">{t('projects.colTasks')}</th>
                  <th className="py-2.5 px-3 w-40 text-right">{t('common.actions')}</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {projects.map((p) => (
                  <tr
                    key={p.id}
                    onClick={() => setSelectedProjectForTasks(p)}
                    className="hover:bg-slate-50/80 cursor-pointer transition-colors group"
                  >
                    {/* Code */}
                    <td className="py-2.5 px-3 font-mono font-medium text-slate-800">
                      <span className="px-1.5 py-0.5 rounded bg-slate-100 border border-slate-200 text-[11px]">
                        {p.code}
                      </span>
                    </td>

                    {/* Name & Description */}
                    <td className="py-2.5 px-3">
                      <div className="font-semibold text-slate-900 group-hover:text-blue-600 transition-colors">
                        {p.name}
                      </div>
                      {p.description && (
                        <div className="text-[11px] text-slate-400 truncate max-w-sm mt-0.5">
                          {p.description}
                        </div>
                      )}
                    </td>

                    {/* Status */}
                    <td className="py-2.5 px-3 whitespace-nowrap">
                      <span
                        className={`inline-block px-1.5 py-0.5 rounded text-[11px] font-medium border ${
                          p.status === 'ACTIVE'
                            ? 'bg-emerald-50 text-emerald-700 border-emerald-200'
                            : p.status === 'PLANNING'
                            ? 'bg-blue-50 text-blue-700 border-blue-200'
                            : p.status === 'COMPLETED'
                            ? 'bg-slate-100 text-slate-700 border-slate-200'
                            : 'bg-amber-50 text-amber-700 border-amber-200'
                        }`}
                      >
                        {t(`projects.status.${p.status}`) || p.status}
                      </span>
                    </td>

                    {/* Dates */}
                    <td className="py-2.5 px-3 text-right whitespace-nowrap">
                      <span className="num-tabular text-[11px] text-slate-600">
                        {formatDateRange(p.startDate, p.endDate)}
                      </span>
                    </td>

                    {/* Owner */}
                    <td className="py-2.5 px-3 text-slate-700 whitespace-nowrap">
                      {p.ownerName ? (
                        <span className="text-xs font-medium">{p.ownerName}</span>
                      ) : (
                        <span className="text-slate-400 text-[11px]">-</span>
                      )}
                    </td>

                    {/* Tasks count */}
                    <td className="py-2.5 px-3 text-right whitespace-nowrap">
                      <span className="num-tabular font-medium text-slate-800">
                        {p.taskCount}
                      </span>
                      <span className="text-slate-400 text-[11px] ml-1">task</span>
                    </td>

                    {/* Actions */}
                    <td
                      className="py-2.5 px-3 text-right whitespace-nowrap space-x-1"
                      onClick={(e) => e.stopPropagation()}
                    >
                      <button
                        type="button"
                        onClick={() => setSelectedProjectForTasks(p)}
                        className="px-2 py-1 rounded bg-slate-900 text-white font-medium text-[11px] hover:bg-slate-800 transition-colors shadow-xs"
                      >
                        {t('projects.openTasks')}
                      </button>
                      <button
                        type="button"
                        onClick={() => handleOpenEdit(p)}
                        className="px-2 py-1 rounded bg-slate-100 hover:bg-slate-200 text-slate-700 font-medium text-[11px] border border-slate-200 transition-colors"
                      >
                        {t('common.edit')}
                      </button>
                      <button
                        type="button"
                        onClick={() => handleDelete(p)}
                        className="px-1.5 py-1 rounded text-rose-600 hover:bg-rose-50 text-[11px] transition-colors"
                        title={t('common.delete')}
                      >
                        ✕
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Project Form Modal (Create / Edit) */}
      <ProjectFormModal
        isOpen={isModalOpen}
        onClose={() => setIsModalOpen(false)}
        project={editingProject}
      />

      {/* Project Tasks Drawer */}
      <ProjectTasksDrawer
        isOpen={!!selectedProjectForTasks}
        project={selectedProjectForTasks}
        onClose={() => setSelectedProjectForTasks(null)}
      />
    </div>
  );
};

export default ProjectsPage;
