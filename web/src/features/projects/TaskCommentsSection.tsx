import React, { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { apiClient } from '../../shared/api/httpClient';
import { useCurrentUser } from '../auth/useCurrentUser';

export interface TaskComment {
  id: string;
  taskId: string;
  authorId: string;
  authorFullName: string;
  authorEmail: string;
  commentText: string;
  createdAt: string;
  updatedAt: string;
}

interface TaskCommentsSectionProps {
  taskId: string;
}

function formatCommentDate(dateStr: string): string {
  try {
    const d = new Date(dateStr);
    return d.toLocaleString('id-ID', {
      day: '2-digit',
      month: 'short',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    });
  } catch {
    return dateStr;
  }
}

export const TaskCommentsSection: React.FC<TaskCommentsSectionProps> = ({ taskId }) => {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const { data: currentUser } = useCurrentUser();
  const [commentText, setCommentText] = useState('');

  const { data: comments = [], isLoading } = useQuery<TaskComment[]>({
    queryKey: ['taskComments', taskId],
    queryFn: () => apiClient.get<TaskComment[]>(`/api/v1/tasks/${taskId}/comments`),
  });

  const addCommentMutation = useMutation({
    mutationFn: (text: string) =>
      apiClient.post<TaskComment>(`/api/v1/tasks/${taskId}/comments`, { commentText: text }),
    onSuccess: () => {
      setCommentText('');
      queryClient.invalidateQueries({ queryKey: ['taskComments', taskId] });
    },
  });

  const deleteCommentMutation = useMutation({
    mutationFn: (commentId: string) =>
      apiClient.delete(`/api/v1/tasks/${taskId}/comments/${commentId}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['taskComments', taskId] });
    },
  });

  const handleAddComment = (e: React.FormEvent) => {
    e.preventDefault();
    if (!commentText.trim()) return;
    addCommentMutation.mutate(commentText);
  };

  const handleDeleteComment = (commentId: string) => {
    if (confirm(t('projects.comments.confirmDelete', 'Hapus komentar ini?'))) {
      deleteCommentMutation.mutate(commentId);
    }
  };

  return (
    <div className="space-y-3 pt-2">
      {/* Comments List */}
      <div className="space-y-2 max-h-56 overflow-y-auto pr-1">
        {isLoading ? (
          <div className="text-center text-xs text-slate-400 py-4">
            {t('common.loading', 'Memuat komentar...')}
          </div>
        ) : comments.length === 0 ? (
          <div className="text-center text-xs text-slate-400 py-6 border border-dashed border-slate-200 rounded">
            {t('projects.comments.noComments', 'Belum ada komentar pada task ini.')}
          </div>
        ) : (
          comments.map((c) => {
            const canDelete =
              currentUser?.role === 'ADMIN' ||
              currentUser?.role === 'SUPERVISOR' ||
              currentUser?.userId === c.authorId;
            return (
              <div
                key={c.id}
                className="p-2.5 rounded bg-slate-50 border border-slate-200 text-xs space-y-1"
              >
                <div className="flex items-center justify-between text-[11px] text-slate-500">
                  <span className="font-semibold text-slate-800">{c.authorFullName}</span>
                  <div className="flex items-center gap-2">
                    <span className="font-mono text-[10px]">{formatCommentDate(c.createdAt)}</span>
                    {canDelete && (
                      <button
                        type="button"
                        onClick={() => handleDeleteComment(c.id)}
                        className="text-rose-500 hover:text-rose-700"
                        title={t('common.delete', 'Hapus')}
                      >
                        ✕
                      </button>
                    )}
                  </div>
                </div>
                <p className="text-slate-700 whitespace-pre-wrap">{c.commentText}</p>
              </div>
            );
          })
        )}
      </div>

      {/* Add Comment Form */}
      <form onSubmit={handleAddComment} className="space-y-2 pt-2 border-t border-slate-100">
        <textarea
          rows={2}
          value={commentText}
          onChange={(e) => setCommentText(e.target.value)}
          placeholder={t('projects.comments.placeholder', 'Tulis komentar atau perkembangan task...')}
          className="w-full px-2.5 py-1.5 rounded border border-slate-200 focus:border-slate-400 focus:outline-hidden text-xs resize-none"
        />
        <div className="flex justify-end">
          <button
            type="submit"
            disabled={!commentText.trim() || addCommentMutation.isPending}
            className="px-3 py-1 bg-slate-900 hover:bg-slate-800 text-white rounded text-xs font-medium disabled:opacity-50 transition-colors"
          >
            {addCommentMutation.isPending
              ? t('common.saving', 'Mengirim...')
              : t('projects.comments.send', 'Kirim Komentar')}
          </button>
        </div>
      </form>
    </div>
  );
};

