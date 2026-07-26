import { useEffect, useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Save } from 'lucide-react';
import { settingsService } from '@/services';
import { extractErrorMessage } from '@/services/api';
import { useToast } from '@/contexts/ToastContext';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/Card';
import { Input } from '@/components/ui/Input';
import { Label } from '@/components/ui/Label';
import { Button } from '@/components/ui/Button';
import { Skeleton } from '@/components/ui/Skeleton';

interface Field {
  key: string;
  label: string;
  type?: string;
  placeholder?: string;
}

const SECTIONS: { title: string; fields: Field[] }[] = [
  {
    title: 'Scheduler',
    fields: [
      { key: 'scheduler.cron', label: 'Cron expression', placeholder: '0 */30 * * * *' },
      { key: 'scheduler.enabled', label: 'Enabled (true/false)', placeholder: 'true' },
    ],
  },
  {
    title: 'Notifications',
    fields: [
      { key: 'notification.score-threshold', label: 'Score threshold', placeholder: '70' },
      { key: 'notification.email.enabled', label: 'Email enabled (true/false)' },
      { key: 'notification.email.to', label: 'Email recipient', type: 'email' },
      { key: 'notification.telegram.enabled', label: 'Telegram enabled (true/false)' },
      { key: 'notification.telegram.bot-token', label: 'Telegram bot token', type: 'password' },
      { key: 'notification.telegram.chat-id', label: 'Telegram chat id' },
    ],
  },
  {
    title: 'API Keys',
    fields: [
      { key: 'integration.adzuna.app-id', label: 'Adzuna App ID' },
      { key: 'integration.adzuna.app-key', label: 'Adzuna App Key', type: 'password' },
      { key: 'integration.jsearch.api-key', label: 'JSearch API Key', type: 'password' },
      { key: 'integration.greenhouse.boards', label: 'Greenhouse boards (comma-separated)' },
      { key: 'integration.lever.companies', label: 'Lever companies (comma-separated)' },
    ],
  },
  {
    title: 'Paths',
    fields: [
      { key: 'profile.resume-path', label: 'Resume path' },
      { key: 'automation.browser-path', label: 'Browser executable path' },
      { key: 'automation.browser-profile-path', label: 'Browser profile path' },
    ],
  },
  {
    title: 'AI & Gmail',
    fields: [
      { key: 'ai.ollama.base-url', label: 'Ollama base URL', placeholder: 'http://192.168.1.x:11434' },
      { key: 'ai.ollama.model', label: 'Ollama model', placeholder: 'qwen2.5:12b' },
      { key: 'gmail.client-id', label: 'Gmail client ID' },
      { key: 'gmail.client-secret', label: 'Gmail client secret', type: 'password' },
      { key: 'gmail.redirect-uri', label: 'Gmail redirect URI', placeholder: 'http://localhost:8080/api/sync/gmail/callback' },
      { key: 'gmail.refresh-token', label: 'Gmail refresh token', type: 'password' },
    ],
  },
];

export function SettingsPage() {
  const qc = useQueryClient();
  const { success, error } = useToast();
  const [values, setValues] = useState<Record<string, string>>({});

  const { data, isLoading } = useQuery({
    queryKey: ['settings'],
    queryFn: settingsService.get,
  });

  useEffect(() => {
    if (data) setValues(data.values);
  }, [data]);

  const mutation = useMutation({
    mutationFn: (payload: Record<string, string>) => settingsService.update(payload),
    onSuccess: () => {
      success('Settings saved');
      qc.invalidateQueries({ queryKey: ['settings'] });
    },
    onError: (e) => error('Save failed', extractErrorMessage(e)),
  });

  const set = (key: string, value: string) => setValues((v) => ({ ...v, [key]: value }));

  if (isLoading) {
    return (
      <div className="space-y-4">
        {Array.from({ length: 4 }).map((_, i) => (
          <Skeleton key={i} className="h-48" />
        ))}
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold">Settings</h1>
          <p className="text-sm text-muted-foreground">
            Manage API keys, scheduler, notifications and paths
          </p>
        </div>
        <Button onClick={() => mutation.mutate(values)} disabled={mutation.isPending}>
          <Save className="h-4 w-4" /> Save changes
        </Button>
      </div>

      <div className="grid gap-6 lg:grid-cols-2">
        {SECTIONS.map((section) => (
          <Card key={section.title}>
            <CardHeader>
              <CardTitle>{section.title}</CardTitle>
            </CardHeader>
            <CardContent className="space-y-4">
              {section.fields.map((f) => (
                <div key={f.key} className="space-y-2">
                  <Label htmlFor={f.key}>{f.label}</Label>
                  <Input
                    id={f.key}
                    type={f.type ?? 'text'}
                    placeholder={f.placeholder}
                    value={values[f.key] ?? ''}
                    onChange={(e) => set(f.key, e.target.value)}
                  />
                </div>
              ))}
            </CardContent>
          </Card>
        ))}
      </div>
    </div>
  );
}
