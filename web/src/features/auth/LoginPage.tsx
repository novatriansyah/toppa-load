import React from 'react';
import { Navigate, useLocation } from 'react-router';
import { useTranslation } from 'react-i18next';
import { useCurrentUser } from './useCurrentUser';

export const LoginPage: React.FC = () => {
  const { t } = useTranslation();
  const location = useLocation();
  const { data: user, isLoading } = useCurrentUser();

  const from = (location.state as { from?: { pathname?: string } })?.from?.pathname || '/beban-kerja';

  if (!isLoading && user) {
    return <Navigate to={from} replace />;
  }

  return (
    <div className="min-h-screen flex flex-col justify-center items-center bg-slate-50 p-4">
      <div className="w-full max-w-sm bg-white border border-slate-200 rounded-md p-6 shadow-xs space-y-6">
        {/* Brand Header */}
        <div className="space-y-1.5 text-center">
          <div className="inline-flex items-center space-x-1.5 px-2 py-0.5 rounded bg-slate-100 text-[11px] font-mono text-slate-600 border border-slate-200 mb-1">
            <span>{t('app.name')}</span>
            <span>·</span>
            <span>{t('app.version')}</span>
          </div>
          <h1 className="text-base font-semibold text-slate-900 tracking-tight">
            {t('auth.loginTitle')}
          </h1>
          <p className="text-xs text-slate-500">
            {t('app.subtitle')}
          </p>
        </div>

        {/* SSO Action */}
        <div className="space-y-3">
          <a
            href="/oauth2/authorization/google"
            className="w-full inline-flex items-center justify-center space-x-2.5 px-3 py-2 bg-slate-900 text-white rounded text-xs font-medium hover:bg-slate-800 transition-colors focus:ring-2 focus:ring-slate-400 focus:outline-none"
          >
            <svg className="w-4 h-4 shrink-0" viewBox="0 0 24 24">
              <path
                fill="#4285F4"
                d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z"
              />
              <path
                fill="#34A853"
                d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"
              />
              <path
                fill="#FBBC05"
                d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.06H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.94l2.85-2.22.81-.63z"
              />
              <path
                fill="#EA4335"
                d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.06l3.66 2.84c.87-2.6 3.3-4.52 6.16-4.52z"
              />
            </svg>
            <span>{t('auth.loginWithGoogle')}</span>
          </a>
        </div>

        {/* Security / Allowlist Note */}
        <div className="p-3 bg-slate-50 border border-slate-200 rounded text-[11px] text-slate-500 space-y-1">
          <div className="font-medium text-slate-700 flex items-center space-x-1">
            <svg className="w-3.5 h-3.5 text-slate-500" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z" />
            </svg>
            <span>{t('auth.allowlistOnly')}</span>
          </div>
          <p>{t('auth.allowlistNotice')}</p>
        </div>
      </div>

      <div className="mt-4 text-center text-[11px] text-slate-400">
        <span>Toppa Load · Internal Work Tool</span>
      </div>
    </div>
  );
};

export default LoginPage;
