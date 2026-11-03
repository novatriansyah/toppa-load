import React, { useEffect, useState } from 'react';
import { NavLink, Outlet } from 'react-router';
import { useTranslation } from 'react-i18next';
import { AuthenticatedActor } from '../features/auth/useCurrentUser';

interface SystemInfo {
  appName?: string;
  version?: string;
  workTimezone?: string;
  currentWorkDate?: string;
}

interface NavItem {
  to: string;
  labelKey: 'workload' | 'projects' | 'allocations' | 'leaves' | 'settings';
  icon: React.ReactNode;
}

const navItems: NavItem[] = [
  {
    to: '/beban-kerja',
    labelKey: 'workload',
    icon: (
      <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
        <path strokeLinecap="round" strokeLinejoin="round" d="M3 10h18M3 14h18M10 3v18M14 3v18M5 3h14a2 2 0 012 2v14a2 2 0 01-2 2H5a2 2 0 01-2-2V5a2 2 0 012-2z" />
      </svg>
    ),
  },
  {
    to: '/proyek',
    labelKey: 'projects',
    icon: (
      <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
        <path strokeLinecap="round" strokeLinejoin="round" d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2m-6 9l2 2 4-4" />
      </svg>
    ),
  },
  {
    to: '/alokasi',
    labelKey: 'allocations',
    icon: (
      <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
        <path strokeLinecap="round" strokeLinejoin="round" d="M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0zm6 3a2 2 0 11-4 0 2 2 0 014 0zM7 10a2 2 0 11-4 0 2 2 0 014 0z" />
      </svg>
    ),
  },
  {
    to: '/cuti',
    labelKey: 'leaves',
    icon: (
      <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
        <path strokeLinecap="round" strokeLinejoin="round" d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z" />
      </svg>
    ),
  },
  {
    to: '/pengaturan',
    labelKey: 'settings',
    icon: (
      <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
        <path strokeLinecap="round" strokeLinejoin="round" d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z" />
        <path strokeLinecap="round" strokeLinejoin="round" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
      </svg>
    ),
  },
];

function getInitials(name?: string): string {
  if (!name) return 'TL';
  const parts = name.trim().split(/\s+/);
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase();
  return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
}

export const AppShell: React.FC = () => {
  const { t } = useTranslation();
  const [systemInfo, setSystemInfo] = useState<SystemInfo | null>(null);
  const [currentUser, setCurrentUser] = useState<AuthenticatedActor | null>(null);

  useEffect(() => {
    let isMounted = true;
    fetch('/api/v1/system/info')
      .then((res) => (res.ok ? res.json() : null))
      .then((data: SystemInfo | null) => {
        if (isMounted && data) {
          setSystemInfo(data);
        }
      })
      .catch(() => {
        // Fallback gracefully in disconnected or mock test environments
      });

    fetch('/api/v1/me')
      .then((res) => (res.ok ? res.json() : null))
      .then((data: AuthenticatedActor | null) => {
        if (isMounted && data) {
          setCurrentUser(data);
        }
      })
      .catch(() => {
        // Fallback gracefully in disconnected or unauthenticated environments
      });

    return () => {
      isMounted = false;
    };
  }, []);

  return (
    <div className="min-h-screen flex flex-col bg-slate-50 text-slate-900 font-sans">
      {/* Precision Top Utility Bar */}
      <header className="h-11 bg-white border-b border-slate-200 px-3 flex items-center justify-between select-none">
        <div className="flex items-center space-x-3">
          <div className="flex items-center space-x-2">
            <span className="font-semibold tracking-tight text-sm text-slate-900">
              {t('app.name')}
            </span>
            <span className="text-[11px] font-mono px-1.5 py-0.5 rounded bg-slate-100 text-slate-600 border border-slate-200">
              {systemInfo?.version || t('app.version')}
            </span>
          </div>
          <span className="text-slate-300">|</span>
          <span className="text-xs text-slate-500 font-medium hidden sm:inline">
            {systemInfo?.workTimezone ? `WIB (${systemInfo.workTimezone})` : t('app.timezone')}
          </span>
        </div>

        <div className="flex items-center space-x-2">
          {/* System status pill */}
          <div className="flex items-center space-x-1 px-2 py-0.5 rounded bg-emerald-50 border border-emerald-200 text-emerald-700 text-xs font-medium">
            <span className="w-1.5 h-1.5 rounded-full bg-emerald-500 animate-pulse" />
            <span>{t('header.statusOnline')}</span>
          </div>

          {/* Notification Bell */}
          <button
            type="button"
            className="p-1 rounded text-slate-500 hover:text-slate-800 hover:bg-slate-100 transition-colors"
            title={t('header.notifications')}
            aria-label={t('header.notifications')}
          >
            <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M15 17h5l-1.405-1.405A2.032 2.032 0 0118 14.158V11a6.002 6.002 0 00-4-5.659V5a2 2 0 10-4 0v.341C7.67 6.165 6 8.388 6 11v3.159c0 .538-.214 1.055-.595 1.436L4 17h5m6 0v1a3 3 0 11-6 0v-1m6 0H9" />
            </svg>
          </button>

          {/* User Profile Badge */}
          <div
            className="flex items-center space-x-1.5 pl-2 border-l border-slate-200 text-xs"
            title={t('header.profile')}
          >
            <div className="w-6 h-6 rounded bg-slate-800 text-white font-medium text-[11px] flex items-center justify-center">
              {getInitials(currentUser?.fullName)}
            </div>
            <div className="hidden md:flex flex-col text-left">
              <span className="text-slate-800 font-medium leading-tight">
                {currentUser?.fullName || 'Internal'}
              </span>
              {currentUser?.role && (
                <span className="text-[10px] text-slate-400 font-mono leading-none">
                  {currentUser.role}
                </span>
              )}
            </div>
            <a
              href="/logout"
              className="text-[11px] text-slate-400 hover:text-rose-600 ml-1 px-1 py-0.5 rounded transition-colors"
              title={t('auth.logout')}
            >
              <svg className="w-3.5 h-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                <path strokeLinecap="round" strokeLinejoin="round" d="M17 16l4-4m0 0l-4-4m4 4H7m6 4v1a3 3 0 01-3 3H6a3 3 0 01-3-3V7a3 3 0 013-3h4a3 3 0 013 3v1" />
              </svg>
            </a>
          </div>
        </div>
      </header>

      {/* Main Container: Sidebar + Content */}
      <div className="flex-1 flex overflow-hidden">
        {/* Compact Navigation Sidebar */}
        <aside className="w-52 bg-white border-r border-slate-200 flex flex-col justify-between shrink-0 select-none">
          <nav role="navigation" className="p-2 space-y-0.5">
            {navItems.map((item) => (
              <NavLink
                key={item.to}
                to={item.to}
                className={({ isActive }) =>
                  `flex items-center space-x-2.5 px-2.5 py-1.5 rounded text-xs font-medium transition-colors ${
                    isActive
                      ? 'bg-slate-100 text-slate-900 border border-slate-300 font-semibold shadow-xs'
                      : 'text-slate-600 hover:text-slate-900 hover:bg-slate-50 border border-transparent'
                  }`
                }
              >
                <span className="text-slate-500 shrink-0">{item.icon}</span>
                <span className="truncate">{t(`nav.${item.labelKey}`)}</span>
              </NavLink>
            ))}
          </nav>

          {/* Footer note: strictly utilitarian */}
          <div className="p-2.5 border-t border-slate-100 text-[11px] text-slate-400">
            <span>Toppa Load · Internal Work Tool</span>
          </div>
        </aside>

        {/* Workspace Content Canvas */}
        <main className="flex-1 overflow-auto p-4 bg-slate-50">
          <Outlet />
        </main>
      </div>
    </div>
  );
};

export default AppShell;
