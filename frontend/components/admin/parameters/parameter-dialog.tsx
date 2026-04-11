'use client';

import { useEffect } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Textarea } from '@/components/ui/textarea';
import { Label } from '@/components/ui/label';
import { Alert, AlertDescription } from '@/components/ui/alert';
import { useCreateParameterSet, useUpdateParameterSet } from '@/hooks/use-parameters';
import type { AiParameterSetResponse } from '@/types/api';

const parameterSetSchema = z.object({
  name: z.string().min(1, 'Ten bo tham so la bat buoc'),
  content: z.string().min(1, 'Noi dung YAML la bat buoc'),
});

type FormValues = z.infer<typeof parameterSetSchema>;

interface ParameterDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  editTarget?: AiParameterSetResponse | null;  // null = create mode
}

export function ParameterDialog({ open, onOpenChange, editTarget }: ParameterDialogProps) {
  const createMutation = useCreateParameterSet();
  const updateMutation = useUpdateParameterSet();
  const isEdit = !!editTarget;

  const form = useForm<FormValues>({
    resolver: zodResolver(parameterSetSchema),
    defaultValues: { name: '', content: '' },
  });

  // Pre-fill form when editing
  useEffect(() => {
    if (editTarget) {
      form.reset({ name: editTarget.name, content: editTarget.content });
    } else {
      form.reset({ name: '', content: '' });
    }
  }, [editTarget, form]);

  const onSubmit = async (values: FormValues) => {
    try {
      if (isEdit && editTarget) {
        await updateMutation.mutateAsync({ id: editTarget.id, data: values });
      } else {
        await createMutation.mutateAsync(values);
      }
      onOpenChange(false);
      form.reset();
    } catch {
      // Error shown inline
    }
  };

  const isPending = createMutation.isPending || updateMutation.isPending;
  const isError = createMutation.isError || updateMutation.isError;

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-2xl">
        <DialogHeader>
          <DialogTitle>
            {isEdit ? 'Chinh sua bo tham so' : 'Tao bo tham so'}
          </DialogTitle>
        </DialogHeader>

        <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="name">Ten bo tham so</Label>
            <Input
              id="name"
              {...form.register('name')}
              placeholder="Nhap ten bo tham so"
            />
            {form.formState.errors.name && (
              <p className="text-xs text-destructive">{form.formState.errors.name.message}</p>
            )}
          </div>

          <div className="space-y-2">
            <Label htmlFor="content">Noi dung YAML</Label>
            <Textarea
              id="content"
              {...form.register('content')}
              className="font-mono text-sm min-h-[320px]"
              placeholder="model:\n  name: openai\n  temperature: 0.3"
            />
            {form.formState.errors.content && (
              <p className="text-xs text-destructive">{form.formState.errors.content.message}</p>
            )}
          </div>

          {isError && (
            <Alert variant="destructive">
              <AlertDescription>
                Thao tac khong thanh cong. Vui long thu lai hoac lien he quan tri vien.
              </AlertDescription>
            </Alert>
          )}

          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
              Huy
            </Button>
            <Button type="submit" disabled={isPending}>
              {isPending ? 'Dang xu ly...' : isEdit ? 'Luu' : 'Tao moi'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
