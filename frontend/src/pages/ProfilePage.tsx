import { useEffect } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Save } from 'lucide-react';
import { profileService } from '@/services';
import { extractErrorMessage } from '@/services/api';
import { useToast } from '@/contexts/ToastContext';
import { REMOTE_PREFERENCES, SENIORITY_LEVELS } from '@/constants';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/Card';
import { Input } from '@/components/ui/Input';
import { Label } from '@/components/ui/Label';
import { Select } from '@/components/ui/Select';
import { Button } from '@/components/ui/Button';
import { Skeleton } from '@/components/ui/Skeleton';
import type { ProfileRequest } from '@/types';

const csv = z
  .string()
  .optional()
  .transform((v) => (v ? v.split(',').map((s) => s.trim()).filter(Boolean) : []));

const schema = z.object({
  fullName: z.string().min(1, 'Full name is required'),
  email: z.string().email('Valid email required'),
  phone: z.string().optional(),
  resumePath: z.string().optional(),
  linkedIn: z.string().optional(),
  github: z.string().optional(),
  portfolio: z.string().optional(),
  titles: csv,
  locations: csv,
  keywords: csv,
  excludedCompanies: csv,
  salaryMinimum: z.coerce.number().min(0).optional(),
  remotePreference: z.enum(REMOTE_PREFERENCES),
  seniority: z.enum(SENIORITY_LEVELS).optional(),
});

type FormValues = z.input<typeof schema>;

export function ProfilePage() {
  const qc = useQueryClient();
  const { success, error } = useToast();

  const { data, isLoading } = useQuery({
    queryKey: ['profile'],
    queryFn: profileService.get,
    retry: false,
  });

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { remotePreference: 'ANY' },
  });

  useEffect(() => {
    if (data) {
      reset({
        fullName: data.fullName,
        email: data.email,
        phone: data.phone ?? '',
        resumePath: data.resumePath ?? '',
        linkedIn: data.linkedIn ?? '',
        github: data.github ?? '',
        portfolio: data.portfolio ?? '',
        titles: data.titles.join(', '),
        locations: data.locations.join(', '),
        keywords: data.keywords.join(', '),
        excludedCompanies: data.excludedCompanies.join(', '),
        salaryMinimum: data.salaryMinimum,
        remotePreference: data.remotePreference,
        seniority: data.seniority,
      });
    }
  }, [data, reset]);

  const mutation = useMutation({
    mutationFn: (payload: ProfileRequest) => profileService.save(payload),
    onSuccess: () => {
      success('Profile saved');
      qc.invalidateQueries({ queryKey: ['profile'] });
    },
    onError: (e) => error('Save failed', extractErrorMessage(e)),
  });

  const onSubmit = handleSubmit((formValues) => {
    const parsed = schema.parse(formValues);
    mutation.mutate(parsed as ProfileRequest);
  });

  if (isLoading) {
    return <Skeleton className="h-[36rem]" />;
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold">Profile</h1>
        <p className="text-sm text-muted-foreground">
          Your preferences drive job scoring and application prep
        </p>
      </div>

      <form onSubmit={onSubmit} className="grid gap-6 lg:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle>Personal</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            <Field label="Full name" error={errors.fullName?.message}>
              <Input {...register('fullName')} />
            </Field>
            <Field label="Email" error={errors.email?.message}>
              <Input type="email" {...register('email')} />
            </Field>
            <Field label="Phone">
              <Input {...register('phone')} />
            </Field>
            <Field label="Resume path">
              <Input {...register('resumePath')} placeholder="/path/to/resume.pdf" />
            </Field>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Links</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            <Field label="LinkedIn">
              <Input {...register('linkedIn')} />
            </Field>
            <Field label="GitHub">
              <Input {...register('github')} />
            </Field>
            <Field label="Portfolio">
              <Input {...register('portfolio')} />
            </Field>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Preferences</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            <Field label="Desired titles (comma-separated)">
              <Input {...register('titles')} placeholder="Backend Engineer, Java Developer" />
            </Field>
            <Field label="Locations (comma-separated)">
              <Input {...register('locations')} placeholder="Remote, Berlin, London" />
            </Field>
            <Field label="Keywords (comma-separated)">
              <Input {...register('keywords')} placeholder="Spring Boot, React, AWS" />
            </Field>
            <Field label="Excluded companies (comma-separated)">
              <Input {...register('excludedCompanies')} />
            </Field>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Targeting</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            <Field label="Minimum salary">
              <Input type="number" {...register('salaryMinimum')} placeholder="80000" />
            </Field>
            <Field label="Remote preference">
              <Select {...register('remotePreference')}>
                {REMOTE_PREFERENCES.map((r) => (
                  <option key={r} value={r}>
                    {r}
                  </option>
                ))}
              </Select>
            </Field>
            <Field label="Seniority">
              <Select {...register('seniority')}>
                <option value="">Any</option>
                {SENIORITY_LEVELS.map((s) => (
                  <option key={s} value={s}>
                    {s}
                  </option>
                ))}
              </Select>
            </Field>
          </CardContent>
        </Card>

        <div className="lg:col-span-2">
          <Button type="submit" disabled={mutation.isPending}>
            <Save className="h-4 w-4" /> Save profile
          </Button>
        </div>
      </form>
    </div>
  );
}

function Field({
  label,
  error,
  children,
}: {
  label: string;
  error?: string;
  children: React.ReactNode;
}) {
  return (
    <div className="space-y-2">
      <Label>{label}</Label>
      {children}
      {error && <p className="text-sm text-destructive">{error}</p>}
    </div>
  );
}
