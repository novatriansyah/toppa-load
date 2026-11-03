import React, { useState } from 'react';
import { Navigate, useLocation, useNavigate } from 'react-router';
import { useTranslation } from 'react-i18next';
import { useQueryClient } from '@tanstack/react-query';
import { useCurrentUser } from './useCurrentUser';
import { apiClient, HttpError } from '../../shared/api/httpClient';

export const LoginPage: React.FC = () => {
  const { t } = useTranslation();
  const location = useLocation();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { data: user, isLoading } = useCurrentUser();

  const [email, setEmail] = useState('admin@toppaload.local');
  const [password, setPassword] = useState('admin123');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const from = (location.state as { from?: { pathname?: string } })?.from?.pathname || '/beban-kerja';

  if (!isLoading && user) {
    return <Navigate to={from} replace />;
  }

  const handlePasswordLogin = async (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMessage(null);
    setIsSubmitting(true);
    try {
      await apiClient.post('/api/v1/auth/login', {
        email: email.trim(),
        password,
      });
      await queryClient.invalidateQueries({ queryKey: ['currentUser'] });
      navigate(from, { replace: true });
    } catch (err: unknown) {
      if (err instanceof HttpError) {
        setErrorMessage(err.problem?.detail || err.message || t('auth.invalidCredentials'));
      } else {
        setErrorMessage(t('auth.invalidCredentials'));
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="min-h-screen flex flex-col justify-center items-center bg-slate-50 p-4">
      <div className="w-full max-w-sm bg-white border border-slate-200 rounded-md p-6 shadow-xs space-y-5">
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

        {/* Dual SSO Actions */}
        <div className="space-y-2.5">
          <a
            href="/oauth2/authorization/microsoft"
            className="w-full inline-flex items-center justify-center space-x-2.5 px-3 py-2 bg-[#2f2f2f] text-white rounded text-xs font-medium hover:bg-[#1f1f1f] transition-colors focus:ring-2 focus:ring-slate-400 focus:outline-none shadow-xs cursor-pointer"
          >
            <svg className="w-4 h-4 shrink-0" viewBox="0 0 21 21">
              <rect x="1" y="1" width="9" height="9" fill="#f25022" />
              <rect x="11" y="1" width="9" height="9" fill="#7fba00" />
              <rect x="1" y="11" width="9" height="9" fill="#00a4ef" />
              <rect x="11" y="11" width="9" height="9" fill="#ffb900" />
            </svg>
            <span>{t('auth.loginWithMicrosoft', 'Masuk dengan Microsoft')}</span>
          </a>

          <a
            href="/oauth2/authorization/google"
            className="w-full inline-flex items-center justify-center space-x-2.5 px-3 py-2 bg-white text-slate-700 border border-slate-300 rounded text-xs font-medium hover:bg-slate-50 transition-colors focus:ring-2 focus:ring-slate-400 focus:outline-none shadow-xs cursor-pointer"
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
            <span>{t('auth.loginWithGoogle', 'Masuk dengan Google')}</span>
          </a>
        </div>

        {/* Divider */}
        <div className="relative flex items-center justify-center my-1">
          <div className="border-t border-slate-200 w-full" />
          <span className="bg-white px-2.5 text-[10px] text-slate-400 absolute uppercase tracking-wider font-medium select-none">
            {t('auth.orPassword', 'atau masuk dengan kata sandi')}
          </span>
        </div>

        {/* Email & Password Form */}
        <form onSubmit={handlePasswordLogin} className="space-y-3">
          {errorMessage && (
            <div className="p-2.5 bg-rose-50 border border-rose-200 text-rose-800 rounded text-xs">
              {errorMessage}
            </div>
          )}

          <div>
            <label className="block text-[11px] font-medium text-slate-700 mb-1">
              {t('auth.emailLabel', 'Alamat Email')}
            </label>
            <input
              type="email"
              required
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="admin@toppaload.local"
              className="w-full text-xs border border-slate-300 rounded px-2.5 py-1.5 focus:outline-hidden focus:ring-1 focus:ring-slate-400 font-mono"
            />
          </div>

          <div>
            <label className="block text-[11px] font-medium text-slate-700 mb-1">
              {t('auth.passwordLabel', 'Kata Sandi')}
            </label>
            <input
              type="password"
              required
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="••••••••"
              className="w-full text-xs border border-slate-300 rounded px-2.5 py-1.5 focus:outline-hidden focus:ring-1 focus:ring-slate-400 font-mono"
            />
          </div>

          <button
            type="submit"
            disabled={isSubmitting}
            className="w-full px-3 py-2 bg-slate-900 hover:bg-slate-800 text-white rounded text-xs font-semibold shadow-xs transition-colors disabled:opacity-50 cursor-pointer"
          >
            {isSubmitting ? t('auth.loggingIn', 'Memverifikasi...') : t('auth.loginButton', 'Masuk')}
          </button>
        </form>

        {/* Developer / Break-Glass Access Tip */}
        <div className="p-2.5 rounded bg-amber-50 border border-amber-200/80 text-[11px] text-amber-900 space-y-1">
          <div className="font-semibold flex items-center gap-1">
            <span>💡</span>
            <span>{t('auth.devHintTitle', 'Akses Pengembang / Break-Glass:')}</span>
          </div>
          <p className="text-slate-600 leading-normal">
            {t('auth.devHintDesc', 'Akun bawaan lokal:')}{' '}
            <code className="font-mono bg-white px-1 py-0.5 rounded border border-amber-200 text-amber-950 font-bold">
              admin@toppaload.local
            </code>{' '}
            / <code className="font-mono bg-white px-1 py-0.5 rounded border border-amber-200 text-amber-950 font-bold">admin123</code>
          </p>
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

        {/* UU PDP Employee Data Transparency Card */}
        <div className="p-3 bg-slate-50 border border-slate-200 rounded text-[11px] text-slate-500 space-y-1">
          <div className="font-medium text-slate-700 flex items-center space-x-1.5">
            <svg className="w-3.5 h-3.5 text-slate-500 shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z" />
            </svg>
            <span>{t('auth.pdpNotice.title')}</span>
          </div>
          <p className="leading-relaxed text-slate-500">{t('auth.pdpNotice.body')}</p>
        </div>
      </div>

      <div className="mt-4 text-center text-[11px] text-slate-400">
        <span>Toppa Load · Internal Work Tool</span>
      </div>
    </div>
  );
};

export default LoginPage;
