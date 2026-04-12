'use client';

import { useState, useEffect } from 'react';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Textarea } from '@/components/ui/textarea';
import { Label } from '@/components/ui/label';
import { Alert, AlertDescription } from '@/components/ui/alert';
import { Skeleton } from '@/components/ui/skeleton';
import { cn } from '@/lib/utils';
import {
  useParameterSets,
  useCreateParameterSet,
  useUpdateParameterSet,
  useActivateParameterSet,
  useCopyParameterSet,
  useDeleteParameterSet,
} from '@/hooks/use-parameters';
import type { AiParameterSetResponse } from '@/types/api';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog';

interface ParsedYaml {
  model?: { name?: string; temperature?: string; maxTokens?: string };
  retrieval?: { topK?: string; similarityThreshold?: string; groundingLimitedThreshold?: string };
  systemPrompt?: string;
}

function parseSimpleYaml(yaml: string): ParsedYaml | null {
  try {
    const lines = yaml.split('\n');
    const result: ParsedYaml = {};
    let currentSection: string | null = null;
    let systemPromptLines: string[] = [];
    let inSystemPrompt = false;

    for (const rawLine of lines) {
      const line = rawLine;
      const trimmed = line.trimStart();

      if (inSystemPrompt) {
        if (/^\w/.test(line) && line.includes(':') && !line.startsWith(' ')) {
          inSystemPrompt = false;
          result.systemPrompt = systemPromptLines.join('\n').trim();
        } else {
          systemPromptLines.push(trimmed);
          continue;
        }
      }

      if (/^model:/.test(trimmed)) {
        currentSection = 'model';
        result.model = {};
        continue;
      }
      if (/^retrieval:/.test(trimmed)) {
        currentSection = 'retrieval';
        result.retrieval = {};
        continue;
      }
      if (/^systemPrompt:\s*\|/.test(trimmed)) {
        currentSection = 'systemPrompt';
        inSystemPrompt = true;
        systemPromptLines = [];
        continue;
      }
      if (/^systemPrompt:\s*(.+)/.test(trimmed)) {
        result.systemPrompt = trimmed.replace(/^systemPrompt:\s*/, '');
        currentSection = null;
        continue;
      }

      const kvMatch = trimmed.match(/^(\w+):\s*(.+)/);
      if (kvMatch && currentSection === 'model' && result.model) {
        result.model[kvMatch[1] as keyof typeof result.model] = kvMatch[2];
      }
      if (kvMatch && currentSection === 'retrieval' && result.retrieval) {
        result.retrieval[kvMatch[1] as keyof typeof result.retrieval] = kvMatch[2];
      }
    }

    if (inSystemPrompt) result.systemPrompt = systemPromptLines.join('\n').trim();
    return result;
  } catch {
    return null;
  }
}

function YamlPreview({ yaml }: { yaml: string }) {
  const parsed = parseSimpleYaml(yaml);

  if (!yaml.trim()) {
    return (
      <div className="text-muted-foreground flex h-full items-center justify-center text-xs">
        Nhập YAML để xem trước
      </div>
    );
  }

  if (!parsed) {
    return (
      <div className="p-3">
        <Badge variant="destructive" className="text-xs">
          YAML không hợp lệ
        </Badge>
      </div>
    );
  }

  return (
    <div className="h-full space-y-3 overflow-y-auto p-3 text-xs">
      {parsed.model && (
        <div>
          <p className="text-muted-foreground mb-1 font-semibold tracking-wide uppercase">Model</p>
          {parsed.model.name && (
            <p>
              <span className="text-muted-foreground">name:</span>{' '}
              <span className="font-mono">{parsed.model.name}</span>
            </p>
          )}
          {parsed.model.temperature && (
            <p>
              <span className="text-muted-foreground">temperature:</span>{' '}
              <span className="font-mono">{parsed.model.temperature}</span>
            </p>
          )}
          {parsed.model.maxTokens && (
            <p>
              <span className="text-muted-foreground">maxTokens:</span>{' '}
              <span className="font-mono">{parsed.model.maxTokens}</span>
            </p>
          )}
        </div>
      )}
      {parsed.retrieval && (
        <div>
          <p className="text-muted-foreground mb-1 font-semibold tracking-wide uppercase">
            Retrieval
          </p>
          {parsed.retrieval.topK && (
            <p>
              <span className="text-muted-foreground">topK:</span>{' '}
              <span className="font-mono">{parsed.retrieval.topK}</span>
            </p>
          )}
          {parsed.retrieval.similarityThreshold && (
            <p>
              <span className="text-muted-foreground">similarityThreshold:</span>{' '}
              <span className="font-mono">{parsed.retrieval.similarityThreshold}</span>
            </p>
          )}
          {parsed.retrieval.groundingLimitedThreshold && (
            <p>
              <span className="text-muted-foreground">groundingLimitedThreshold:</span>{' '}
              <span className="font-mono">{parsed.retrieval.groundingLimitedThreshold}</span>
            </p>
          )}
        </div>
      )}
      {parsed.systemPrompt && (
        <div>
          <p className="text-muted-foreground mb-1 font-semibold tracking-wide uppercase">
            System Prompt
          </p>
          <p className="text-muted-foreground line-clamp-6 whitespace-pre-wrap">
            {parsed.systemPrompt}
          </p>
        </div>
      )}
      {!parsed.model && !parsed.retrieval && !parsed.systemPrompt && (
        <p className="text-muted-foreground">Không nhận ra cấu trúc YAML chuẩn</p>
      )}
    </div>
  );
}

const schema = z.object({
  name: z.string().min(1, 'Tên bộ tham số là bắt buộc'),
  content: z.string().min(1, 'Nội dung YAML là bắt buộc'),
});
type FormValues = z.infer<typeof schema>;

export default function ParametersPage() {
  const { data: parameterSets, isLoading } = useParameterSets();
  const [selected, setSelected] = useState<AiParameterSetResponse | null>(null);
  const [isNew, setIsNew] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState<AiParameterSetResponse | null>(null);

  const createMutation = useCreateParameterSet();
  const updateMutation = useUpdateParameterSet();
  const activate = useActivateParameterSet();
  const copy = useCopyParameterSet();
  const deleteMutation = useDeleteParameterSet();

  const form = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { name: '', content: '' },
  });

  const [previewYaml, setPreviewYaml] = useState('');
  const watchedContent = form.watch('content');

  useEffect(() => {
    const timer = setTimeout(() => {
      setPreviewYaml(watchedContent ?? '');
    }, 300);
    return () => clearTimeout(timer);
  }, [watchedContent]);

  const openNew = () => {
    setSelected(null);
    setIsNew(true);
    form.reset({ name: '', content: '' });
  };

  const openEdit = (ps: AiParameterSetResponse) => {
    setSelected(ps);
    setIsNew(false);
    form.reset({ name: ps.name, content: ps.content });
  };

  const onSubmit = async (values: FormValues) => {
    if (isNew) {
      await createMutation.mutateAsync({ name: values.name, content: values.content });
      setIsNew(false);
      form.reset();
    } else if (selected) {
      await updateMutation.mutateAsync({
        id: selected.id,
        data: { name: values.name, content: values.content },
      });
    }
  };

  const isPending = createMutation.isPending || updateMutation.isPending;
  const isError = createMutation.isError || updateMutation.isError;
  const showEditor = isNew || selected !== null;

  return (
    <div className="flex h-screen flex-col gap-4 p-6">
      <div className="flex flex-shrink-0 items-center justify-between">
        <h1 className="text-xl font-semibold">Bộ tham số AI</h1>
        <Button onClick={openNew}>+ Tạo mới</Button>
      </div>

      <div className="flex min-h-0 flex-1 gap-4">
        {/* Danh sách bên trái */}
        <div className="w-64 flex-shrink-0 overflow-y-auto rounded-lg border">
          {isLoading && (
            <div className="space-y-2 p-3">
              {Array.from({ length: 3 }).map((_, i) => (
                <Skeleton key={i} className="h-10 w-full" />
              ))}
            </div>
          )}
          {parameterSets?.map((ps) => (
            <div
              key={ps.id}
              onClick={() => openEdit(ps)}
              className={cn(
                'hover:bg-muted cursor-pointer border-b p-3 transition-colors',
                selected?.id === ps.id && !isNew && 'bg-primary/10 border-l-primary border-l-2',
              )}
            >
              <div className="flex flex-wrap items-center gap-2">
                <span className="truncate text-sm font-medium">{ps.name}</span>
                {ps.active && <Badge className="text-xs">Đang hoạt động</Badge>}
              </div>
              <div className="mt-2 flex gap-1" onClick={(e) => e.stopPropagation()}>
                {!ps.active && (
                  <Button
                    size="sm"
                    variant="outline"
                    className="h-6 px-2 text-xs"
                    onClick={() => activate.mutate(ps.id)}
                    disabled={activate.isPending}
                  >
                    Kích hoạt
                  </Button>
                )}
                <Button
                  size="sm"
                  variant="outline"
                  className="h-6 px-2 text-xs"
                  onClick={() => copy.mutate(ps.id)}
                  disabled={copy.isPending}
                >
                  Sao chép
                </Button>
                {!ps.active && (
                  <Button
                    size="sm"
                    variant="outline"
                    className="text-destructive border-destructive hover:bg-destructive hover:text-destructive-foreground h-6 px-2 text-xs"
                    onClick={() => setDeleteTarget(ps)}
                  >
                    Xóa
                  </Button>
                )}
              </div>
            </div>
          ))}
          {!isLoading && parameterSets?.length === 0 && (
            <p className="text-muted-foreground p-4 text-center text-sm">Chưa có bộ tham số</p>
          )}
        </div>

        {/* Editor bên phải */}
        <div className="flex flex-1 flex-col overflow-hidden rounded-lg border">
          {!showEditor ? (
            <div className="text-muted-foreground flex h-full items-center justify-center text-sm">
              Chọn bộ tham số để chỉnh sửa hoặc nhấn &quot;+ Tạo mới&quot;
            </div>
          ) : (
            <form onSubmit={form.handleSubmit(onSubmit)} className="flex h-full flex-col gap-4 p-4">
              <div className="flex-shrink-0 space-y-1">
                <Label htmlFor="name">Tên bộ tham số</Label>
                <Input id="name" {...form.register('name')} placeholder="Nhập tên bộ tham số" />
                {form.formState.errors.name && (
                  <p className="text-destructive text-xs">{form.formState.errors.name.message}</p>
                )}
              </div>

              <div className="flex min-h-0 flex-1 gap-3">
                {/* Left: YAML textarea */}
                <div className="flex min-h-0 flex-1 flex-col space-y-1">
                  <Label htmlFor="content">Cấu hình YAML</Label>
                  <Textarea
                    id="content"
                    {...form.register('content')}
                    className="flex-1 resize-none font-mono text-sm"
                    placeholder={
                      'model:\n  name: claude-sonnet-4-6\n  temperature: 0.3\n  maxTokens: 2048\nretrieval:\n  topK: 5\n  similarityThreshold: 0.7\n  groundingLimitedThreshold: 0.5\nsystemPrompt: |\n  ...'
                    }
                  />
                  {form.formState.errors.content && (
                    <p className="text-destructive text-xs">
                      {form.formState.errors.content.message}
                    </p>
                  )}
                </div>
                {/* Right: live preview */}
                <div className="flex min-h-0 w-56 flex-shrink-0 flex-col rounded-md border">
                  <p className="text-muted-foreground flex-shrink-0 border-b px-3 pt-2 pb-1 text-xs font-semibold">
                    Xem trước
                  </p>
                  <YamlPreview yaml={previewYaml} />
                </div>
              </div>

              {isError && (
                <Alert variant="destructive" className="flex-shrink-0">
                  <AlertDescription>Thao tác không thành công. Vui lòng thử lại.</AlertDescription>
                </Alert>
              )}

              <div className="flex flex-shrink-0 justify-end gap-2">
                <Button
                  type="button"
                  variant="outline"
                  onClick={() => {
                    setSelected(null);
                    setIsNew(false);
                    form.reset();
                  }}
                >
                  Hủy
                </Button>
                <Button type="submit" disabled={isPending}>
                  {isPending ? 'Đang xử lý...' : isNew ? 'Tạo mới' : 'Lưu thay đổi'}
                </Button>
              </div>
            </form>
          )}
        </div>
      </div>

      <AlertDialog open={!!deleteTarget} onOpenChange={() => setDeleteTarget(null)}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Xác nhận xóa</AlertDialogTitle>
            <AlertDialogDescription>
              Xóa bộ tham số &quot;{deleteTarget?.name}&quot;? Dữ liệu sẽ bị xóa vĩnh viễn.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Hủy</AlertDialogCancel>
            <AlertDialogAction
              className="bg-destructive text-destructive-foreground"
              onClick={() => {
                if (deleteTarget) {
                  deleteMutation.mutate(deleteTarget.id);
                  if (selected?.id === deleteTarget.id) setSelected(null);
                  setDeleteTarget(null);
                }
              }}
            >
              Xóa
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  );
}
