'use client';

import { useState } from 'react';
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
  useAllowedModels,
} from '@/hooks/use-parameters';
import type { AiParameterSetResponse } from '@/types/api';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
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
import { Tabs, TabsList, TabsTrigger, TabsContent } from '@/components/ui/tabs';
import { parse as parseYaml, stringify as stringifyYaml } from 'yaml';

// ─── YAML helpers ───────────────────────────────────────────────────────────

interface ParameterYaml {
  retrieval?: {
    topK?: number;
    similarityThreshold?: number;
  };
  systemPrompt?: string;
  messages?: {
    disclaimer?: string;
    refusal?: string;
    refusalNextStep1?: string;
    refusalNextStep2?: string;
    refusalNextStep3?: string;
  };
  [key: string]: unknown;
}

function yamlToForm(content: string): Partial<FormValues> {
  try {
    const parsed = parseYaml(content) as ParameterYaml;
    return {
      retrievalTopK: String(parsed?.retrieval?.topK ?? '5'),
      retrievalSimilarityThreshold: String(parsed?.retrieval?.similarityThreshold ?? '0.25'),
      systemPrompt: parsed?.systemPrompt ?? '',
      messagesDisclaimer: parsed?.messages?.disclaimer ?? '',
      messagesRefusal: parsed?.messages?.refusal ?? '',
      messagesRefusalNextStep1: parsed?.messages?.refusalNextStep1 ?? '',
      messagesRefusalNextStep2: parsed?.messages?.refusalNextStep2 ?? '',
      messagesRefusalNextStep3: parsed?.messages?.refusalNextStep3 ?? '',
    };
  } catch {
    return {};
  }
}

function formToYaml(values: FormValues, existingContent?: string): string {
  const obj: ParameterYaml = {
    retrieval: {
      topK: parseInt(values.retrievalTopK, 10),
      similarityThreshold: parseFloat(values.retrievalSimilarityThreshold),
    },
    systemPrompt: values.systemPrompt,
    messages: {
      disclaimer: values.messagesDisclaimer,
      refusal: values.messagesRefusal,
      refusalNextStep1: values.messagesRefusalNextStep1,
      refusalNextStep2: values.messagesRefusalNextStep2,
      refusalNextStep3: values.messagesRefusalNextStep3,
    },
  };

  return stringifyYaml(obj);
}

// ─── Schema ─────────────────────────────────────────────────────────────────

const schema = z.object({
  name: z.string().min(1, 'Tên bộ tham số là bắt buộc'),
  chatModel: z.string().optional(),
  evaluatorModel: z.string().optional(),
  retrievalTopK: z.string().min(1),
  retrievalSimilarityThreshold: z.string().min(1),
  systemPrompt: z.string().min(1, 'System prompt là bắt buộc'),
  messagesDisclaimer: z.string(),
  messagesRefusal: z.string(),
  messagesRefusalNextStep1: z.string(),
  messagesRefusalNextStep2: z.string(),
  messagesRefusalNextStep3: z.string(),
});

type FormValues = z.infer<typeof schema>;

const DEFAULT_VALUES: FormValues = {
  name: '',
  chatModel: '',
  evaluatorModel: '',
  retrievalTopK: '5',
  retrievalSimilarityThreshold: '0.25',
  systemPrompt:
    'Bạn là trợ lý hỏi đáp pháp luật giao thông Việt Nam.\nHãy trả lời bằng tiếng Việt với giọng điệu rõ ràng, trang trọng, dễ hiểu.\nThông tin chỉ mang tính chất tham khảo, không phải tư vấn pháp lý chính thức.',
  messagesDisclaimer:
    'Thông tin chỉ nhằm mục đích tham khảo, không thay thế tư vấn pháp lý chính thức.',
  messagesRefusal:
    'Tôi chưa thể trả lời chắc chắn vì chưa tìm thấy đủ căn cứ đáng tin cậy trong nguồn pháp lý đã được phê duyệt.',
  messagesRefusalNextStep1: 'Nêu rõ hành vi vi phạm, loại phương tiện, thời gian hoặc địa điểm.',
  messagesRefusalNextStep2: 'Nếu bạn đang hỏi về giấy tờ hoặc thủ tục, hãy ghi rõ tên giấy tờ.',
  messagesRefusalNextStep3: 'Ưu tiên đối chiếu thêm với văn bản hoặc cổng thông tin chính thức.',
};

// ─── Section helpers ─────────────────────────────────────────────────────────

function SectionHeader({ children }: { children: React.ReactNode }) {
  return (
    <p className="text-muted-foreground mt-4 mb-2 text-xs font-semibold tracking-wide uppercase">
      {children}
    </p>
  );
}

function FieldRow({
  label,
  hint,
  children,
}: {
  label: string;
  hint?: string;
  children: React.ReactNode;
}) {
  return (
    <div className="space-y-1">
      <Label className="text-sm">
        {label}
        {hint && <span className="text-muted-foreground ml-1 text-xs font-normal">({hint})</span>}
      </Label>
      {children}
    </div>
  );
}

// ─── Main page ───────────────────────────────────────────────────────────────

export default function ParametersPage() {
  const { data: parameterSets, isLoading } = useParameterSets();
  const { data: allowedModels = [] } = useAllowedModels();
  const [selected, setSelected] = useState<AiParameterSetResponse | null>(null);
  const [isNew, setIsNew] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState<AiParameterSetResponse | null>(null);
  const [existingContent, setExistingContent] = useState<string | undefined>(undefined);

  const createMutation = useCreateParameterSet();
  const updateMutation = useUpdateParameterSet();
  const activate = useActivateParameterSet();
  const copy = useCopyParameterSet();
  const deleteMutation = useDeleteParameterSet();

  const form = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: DEFAULT_VALUES,
  });

  const watchedValues = form.watch();

  const openNew = () => {
    setSelected(null);
    setIsNew(true);
    setExistingContent(undefined);
    form.reset(DEFAULT_VALUES);
  };

  const openEdit = (ps: AiParameterSetResponse) => {
    setSelected(ps);
    setIsNew(false);
    setExistingContent(ps.content);
    const parsed = yamlToForm(ps.content);
    form.reset({
      ...DEFAULT_VALUES,
      ...parsed,
      name: ps.name,
      chatModel: ps.chatModel ?? '',
      evaluatorModel: ps.evaluatorModel ?? '',
    });
  };

  const onSubmit = async (values: FormValues) => {
    const content = formToYaml(values, existingContent);
    if (isNew) {
      await createMutation.mutateAsync({
        name: values.name,
        content,
        chatModel: values.chatModel || undefined,
        evaluatorModel: values.evaluatorModel || undefined,
      });
      setIsNew(false);
      form.reset(DEFAULT_VALUES);
    } else if (selected) {
      await updateMutation.mutateAsync({
        id: selected.id,
        data: {
          name: values.name,
          content,
          chatModel: values.chatModel || undefined,
          evaluatorModel: values.evaluatorModel || undefined,
        },
      });
      setExistingContent(content);
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
        {/* Parameter set list */}
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

        {/* Editor */}
        <div className="flex flex-1 flex-col overflow-hidden rounded-lg border">
          {!showEditor ? (
            <div className="text-muted-foreground flex h-full items-center justify-center text-sm">
              Chọn bộ tham số để chỉnh sửa hoặc nhấn &quot;+ Tạo mới&quot;
            </div>
          ) : (
            <form
              onSubmit={form.handleSubmit(onSubmit)}
              className="flex h-full flex-col overflow-hidden"
            >
              <Tabs defaultValue="form" className="flex min-h-0 flex-1 flex-col">
                <div className="flex-shrink-0 border-b px-5 pt-3">
                  <TabsList variant="line">
                    <TabsTrigger value="form">Cấu hình</TabsTrigger>
                    <TabsTrigger value="yaml">YAML</TabsTrigger>
                  </TabsList>
                </div>
                <TabsContent value="form" className="min-h-0 overflow-y-auto">
                  <div className="space-y-2 p-5">
                    {/* Name */}
                    <FieldRow label="Tên bộ tham số">
                      <Input {...form.register('name')} placeholder="Ví dụ: Bộ tham số mặc định" />
                      {form.formState.errors.name && (
                        <p className="text-destructive text-xs">
                          {form.formState.errors.name.message}
                        </p>
                      )}
                    </FieldRow>

                    {/* Model selection */}
                    <SectionHeader>Mô hình AI</SectionHeader>
                    <div className="grid grid-cols-2 gap-3">
                      <FieldRow label="Chat model">
                        <Select
                          value={form.watch('chatModel') ?? ''}
                          onValueChange={(val) => form.setValue('chatModel', val ?? '')}
                        >
                          <SelectTrigger>
                            <SelectValue placeholder="-- Không chỉ định --" />
                          </SelectTrigger>
                          <SelectContent>
                            <SelectItem value="">-- Không chỉ định --</SelectItem>
                            {allowedModels.map((m) => (
                              <SelectItem key={m.modelId} value={m.modelId}>
                                {m.displayName}
                              </SelectItem>
                            ))}
                          </SelectContent>
                        </Select>
                      </FieldRow>
                      <FieldRow label="Evaluator model">
                        <Select
                          value={form.watch('evaluatorModel') ?? ''}
                          onValueChange={(val) => form.setValue('evaluatorModel', val ?? '')}
                        >
                          <SelectTrigger>
                            <SelectValue placeholder="-- Không chỉ định --" />
                          </SelectTrigger>
                          <SelectContent>
                            <SelectItem value="">-- Không chỉ định --</SelectItem>
                            {allowedModels.map((m) => (
                              <SelectItem key={m.modelId} value={m.modelId}>
                                {m.displayName}
                              </SelectItem>
                            ))}
                          </SelectContent>
                        </Select>
                      </FieldRow>
                    </div>

                    {/* Retrieval */}
                    <SectionHeader>Truy xuất ngữ nghĩa</SectionHeader>
                    <div className="grid grid-cols-2 gap-3">
                      <FieldRow label="Top K" hint="số tài liệu">
                        <Input type="number" min="1" {...form.register('retrievalTopK')} />
                      </FieldRow>
                      <FieldRow label="Ngưỡng tương đồng" hint="0 – 1">
                        <Input
                          type="number"
                          step="0.01"
                          min="0"
                          max="1"
                          {...form.register('retrievalSimilarityThreshold')}
                        />
                      </FieldRow>
                    </div>

                    {/* System Prompt */}
                    <SectionHeader>System Prompt</SectionHeader>
                    <Textarea
                      {...form.register('systemPrompt')}
                      className="min-h-[100px] resize-y text-sm"
                      placeholder="Bạn là trợ lý hỏi đáp pháp luật giao thông Việt Nam..."
                    />
                    {form.formState.errors.systemPrompt && (
                      <p className="text-destructive text-xs">
                        {form.formState.errors.systemPrompt.message}
                      </p>
                    )}

                    {/* Messages */}
                    <SectionHeader>Thông điệp hệ thống</SectionHeader>
                    <div className="space-y-3">
                      <FieldRow label="Tuyên bố miễn trách nhiệm">
                        <Input {...form.register('messagesDisclaimer')} />
                      </FieldRow>
                      <FieldRow label="Phản hồi từ chối">
                        <Textarea
                          {...form.register('messagesRefusal')}
                          className="min-h-[60px] resize-y text-sm"
                        />
                      </FieldRow>
                      <FieldRow label="Bước tiếp theo khi từ chối — 1">
                        <Input {...form.register('messagesRefusalNextStep1')} />
                      </FieldRow>
                      <FieldRow label="Bước tiếp theo khi từ chối — 2">
                        <Input {...form.register('messagesRefusalNextStep2')} />
                      </FieldRow>
                      <FieldRow label="Bước tiếp theo khi từ chối — 3">
                        <Input {...form.register('messagesRefusalNextStep3')} />
                      </FieldRow>
                    </div>
                  </div>
                </TabsContent>

                <TabsContent value="yaml" className="min-h-0 overflow-auto p-5">
                  <pre className="bg-muted h-full min-h-[200px] rounded-md p-4 text-xs leading-relaxed break-all whitespace-pre-wrap">
                    {formToYaml(watchedValues, existingContent)}
                  </pre>
                </TabsContent>
              </Tabs>

              {isError && (
                <div className="flex-shrink-0 px-5 pb-2">
                  <Alert variant="destructive">
                    <AlertDescription>
                      Thao tác không thành công. Vui lòng thử lại.
                    </AlertDescription>
                  </Alert>
                </div>
              )}

              <div className="flex flex-shrink-0 justify-end gap-2 border-t px-5 py-3">
                <Button
                  type="button"
                  variant="outline"
                  onClick={() => {
                    setSelected(null);
                    setIsNew(false);
                    form.reset(DEFAULT_VALUES);
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
