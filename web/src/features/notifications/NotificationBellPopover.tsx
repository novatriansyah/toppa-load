import React, { useState, useRef, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '../../shared/api/httpClient';

export interface NotificationItem {
  id: string;
  type: 'ALLOCATION_ASSIGNED' | 'OVERLOAD_WARNING' | 'DEADLINE_APPROACHING' | 'LEAVE_STATUS_CHANGED';
  title: string;
  payload: Record<string, unknown>;
  readAt: string | null;
  emailStatus: string;
  createdAt: string;
}

export interface NotificationListResponse {
  items: NotificationItem[];
  unreadCount: number;
}

export const NotificationBellPopover: React.FC = () => {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const [isOpen, setIsOpen] = useState(false);
  const popoverRef = useRef<HTMLDivElement>(null);

  const { data } = useQuery<NotificationListResponse>({
    queryKey: ['notifications'],
    queryFn: async () => {
      return apiClient.get<NotificationListResponse>('/api/v1/notifications');
    },
    refetchInterval: 30000,
  });

  const markAllMutation = useMutation({
    mutationFn: async () => {
      return apiClient.post<{ markedCount: number }>('/api/v1/notifications/read-all');
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['notifications'] });
    },
  });

  const markOneMutation = useMutation({
    mutationFn: async (id: string) => {
      return apiClient.post(`/api/v1/notifications/${id}/read`);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['notifications'] });
    },
  });

  // Close on outside click
  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (popoverRef.current && !popoverRef.current.contains(event.target as Node)) {
        setIsOpen(false);
      }
    };
    if (isOpen) {
      document.addEventListener('mousedown', handleClickOutside);
    }
    return () => {
      document.removeEventListener('mousedown', handleClickOutside);
    };
  }, [isOpen]);

  const unreadCount = data?.unreadCount ?? 0;
  const items = data?.items ?? [];

  const getTypeBadgeClass = (type: string) => {
    switch (type) {
      case 'OVERLOAD_WARNING':
        return 'bg-rose-100 text-rose-700 border-rose-200';
      case 'DEADLINE_APPROACHING':
        return 'bg-amber-100 text-amber-700 border-amber-200';
      case 'ALLOCATION_ASSIGNED':
        return 'bg-blue-100 text-blue-700 border-blue-200';
      default:
        return 'bg-slate-100 text-slate-700 border-slate-200';
    }
  };

  const formatTimestamp = (isoString: string) => {
    try {
      const date = new Date(isoString);
      return date.toLocaleDateString('id-ID', {
        month: 'short',
        day: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
      });
    } catch {
      return isoString;
    }
  };

  return (
    <div className="relative inline-block" ref={popoverRef}>
      <button
        type="button"
        onClick={() => setIsOpen(!isOpen)}
        className="relative p-1 rounded text-slate-500 hover:text-slate-800 hover:bg-slate-100 transition-colors cursor-pointer"
        title={t('header.notifications')}
        aria-label={t('header.notifications')}
        data-testid="notification-bell"
      >
        <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
          <path
            strokeLinecap="round"
            strokeLinejoin="round"
            d="M15 17h5l-1.405-1.405A2.032 2.032 0 0118 14.158V11a6.002 6.002 0 00-4-5.659V5a2 2 0 10-4 0v.341C7.67 6.165 6 8.388 6 11v3.159c0 .538-.214 1.055-.595 1.436L4 17h5m6 0v1a3 3 0 11-6 0v-1m6 0H9"
          />
        </svg>

        {unreadCount > 0 && (
          <span
            data-testid="unread-badge"
            className="absolute -top-1 -right-1 px-1 min-w-[16px] h-4 text-[10px] font-bold text-white bg-rose-600 rounded-full flex items-center justify-center leading-none shadow-xs"
          >
            {unreadCount > 99 ? '99+' : unreadCount}
          </span>
        )}
      </button>

      {isOpen && (
        <div
          data-testid="notifications-popover"
          className="absolute right-0 mt-2 w-80 sm:w-96 bg-white border border-slate-200 rounded-md shadow-lg z-50 overflow-hidden text-xs"
        >
          {/* Header */}
          <div className="px-3 py-2 bg-slate-50 border-b border-slate-200 flex items-center justify-between">
            <div className="flex items-center space-x-2">
              <span className="font-semibold text-slate-900">{t('notifications.title')}</span>
              {unreadCount > 0 && (
                <span className="text-[11px] px-1.5 py-0.2 rounded bg-rose-50 text-rose-700 border border-rose-200 font-medium">
                  {t('notifications.unreadCount', { count: unreadCount })}
                </span>
              )}
            </div>
            {unreadCount > 0 && (
              <button
                type="button"
                onClick={() => markAllMutation.mutate()}
                disabled={markAllMutation.isPending}
                className="text-[11px] text-blue-600 hover:text-blue-800 font-medium cursor-pointer transition-colors"
                data-testid="mark-all-read-btn"
              >
                {markAllMutation.isPending ? t('notifications.marking') : t('notifications.markAllRead')}
              </button>
            )}
          </div>

          {/* List items */}
          <div className="max-h-80 overflow-y-auto divide-y divide-slate-100">
            {items.length === 0 ? (
              <div className="p-4 text-center text-slate-400 italic">
                {t('notifications.empty')}
              </div>
            ) : (
              items.map((item) => {
                const isUnread = !item.readAt;
                return (
                  <div
                    key={item.id}
                    onClick={() => {
                      if (isUnread) markOneMutation.mutate(item.id);
                    }}
                    className={`p-3 transition-colors cursor-pointer ${
                      isUnread ? 'bg-blue-50/40 hover:bg-blue-50/70' : 'hover:bg-slate-50'
                    }`}
                    data-testid={`notification-item-${item.id}`}
                  >
                    <div className="flex items-start justify-between gap-2">
                      <div className="flex items-center space-x-1.5">
                        <span
                          className={`text-[10px] font-medium px-1.5 py-0.5 rounded border ${getTypeBadgeClass(
                            item.type
                          )}`}
                        >
                          {t(`notifications.types.${item.type}`, { defaultValue: item.type })}
                        </span>
                        {isUnread && (
                          <span className="w-1.5 h-1.5 rounded-full bg-blue-600 shrink-0" />
                        )}
                      </div>
                      <span className="text-[10px] text-slate-400 shrink-0 font-mono">
                        {formatTimestamp(item.createdAt)}
                      </span>
                    </div>
                    <p className="mt-1 text-slate-800 font-medium leading-snug">{item.title}</p>
                  </div>
                );
              })
            )}
          </div>
        </div>
      )}
    </div>
  );
};

