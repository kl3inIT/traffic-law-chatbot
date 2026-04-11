'use client';

import { useState } from 'react';
import { Check, ChevronsUpDown } from 'lucide-react';
import { ColumnDef, flexRender, getCoreRowModel, useReactTable } from '@tanstack/react-table';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import {
  Command,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
} from '@/components/ui/command';
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover';
import { Skeleton } from '@/components/ui/skeleton';
import { cn } from '@/lib/utils';
import { ChunkDetailSheet } from './chunk-detail-sheet';
import { useChunks } from '@/hooks/use-index';
import { useAllSources } from '@/hooks/use-sources';
import type { ChunkSummaryResponse } from '@/types/api';

const ALL_SOURCES = '__all__';

function truncateUuid(id: string): string {
  return id.length > 8 ? `${id.slice(0, 8)}…` : id;
}

function ApprovalBadge({ state }: { state: string | null }) {
  if (!state) return null;
  const cls =
    state === 'APPROVED'
      ? 'bg-blue-50 text-blue-700 border-blue-200'
      : state === 'PENDING'
        ? 'bg-yellow-50 text-yellow-700 border-yellow-200'
        : state === 'REJECTED'
          ? 'bg-red-50 text-red-700 border-red-200'
          : 'bg-gray-100 text-gray-600';
  const label =
    state === 'APPROVED'
      ? 'Đã duyệt'
      : state === 'PENDING'
        ? 'Chờ duyệt'
        : state === 'REJECTED'
          ? 'Từ chối'
          : state;
  return (
    <Badge variant="outline" className={`text-xs ${cls}`}>
      {label}
    </Badge>
  );
}

function TrustedBadge({ value }: { value: string | null }) {
  if (value !== 'true') return null;
  return (
    <Badge variant="outline" className="border-emerald-200 bg-emerald-50 text-xs text-emerald-700">
      Tin cậy
    </Badge>
  );
}

function ActiveBadge({ value }: { value: string | null }) {
  if (value !== 'true') return null;
  return (
    <Badge variant="outline" className="border-green-200 bg-green-50 text-xs text-green-700">
      Hoạt động
    </Badge>
  );
}

function MetadataBadges({ chunk }: { chunk: ChunkSummaryResponse }) {
  const hasMeta = chunk.sectionRef || chunk.pageNumber > 0 || chunk.chunkOrdinal >= 0;
  if (!hasMeta) return null;
  return (
    <div className="flex flex-wrap gap-1">
      {chunk.pageNumber > 0 && (
        <Badge variant="secondary" className="text-xs">
          Tr. {chunk.pageNumber}
        </Badge>
      )}
      {chunk.sectionRef && (
        <Badge variant="secondary" className="max-w-[100px] truncate text-xs">
          {chunk.sectionRef}
        </Badge>
      )}
      <Badge variant="secondary" className="text-xs">
        #{chunk.chunkOrdinal}
      </Badge>
    </div>
  );
}

function buildColumns(onViewDetail: (id: string) => void): ColumnDef<ChunkSummaryResponse>[] {
  return [
    {
      accessorKey: 'id',
      header: 'ID đoạn',
      cell: ({ row }) => (
        <span className="text-muted-foreground font-mono text-xs">
          {truncateUuid(row.original.id)}
        </span>
      ),
    },
    {
      accessorKey: 'sourceId',
      header: 'Nguồn',
      cell: ({ row }) => (
        <span className="text-muted-foreground block max-w-[120px] truncate font-mono text-xs">
          {row.original.sourceId ? truncateUuid(row.original.sourceId) : '—'}
        </span>
      ),
    },
    {
      id: 'metadata',
      header: 'Siêu dữ liệu',
      cell: ({ row }) => <MetadataBadges chunk={row.original} />,
    },
    {
      accessorKey: 'approvalState',
      header: 'Trạng thái',
      cell: ({ row }) => (
        <div className="flex flex-wrap gap-1">
          <ApprovalBadge state={row.original.approvalState} />
          <TrustedBadge value={row.original.trusted} />
          <ActiveBadge value={row.original.active} />
        </div>
      ),
    },
    {
      id: 'actions',
      header: '',
      cell: ({ row }) => (
        <Button
          variant="ghost"
          size="sm"
          className="h-7 px-2 text-xs"
          onClick={() => onViewDetail(row.original.id)}
        >
          Xem chi tiết
        </Button>
      ),
    },
  ];
}

export function IndexChunkTable() {
  const [selectedSourceId, setSelectedSourceId] = useState<string | undefined>(undefined);
  const [selectedChunkId, setSelectedChunkId] = useState<string | null>(null);
  const [page, setPage] = useState(0);
  const pageSize = 20;

  const [comboOpen, setComboOpen] = useState(false);
  const { data: sourcesPage, isLoading: sourcesLoading } = useAllSources();
  const { data: chunksPage, isLoading: chunksLoading } = useChunks(
    selectedSourceId,
    page,
    pageSize,
  );

  const columns = buildColumns(setSelectedChunkId);

  const table = useReactTable({
    data: chunksPage?.content ?? [],
    columns,
    getCoreRowModel: getCoreRowModel(),
  });

  const totalPages = chunksPage?.totalPages ?? 0;
  const totalElements = chunksPage?.totalElements ?? 0;

  const handleSourceChange = (value: string | null) => {
    if (!value || value === ALL_SOURCES) {
      setSelectedSourceId(undefined);
    } else {
      setSelectedSourceId(value);
    }
    setPage(0);
  };

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h2 className="text-base font-semibold">
            Đoạn văn bản đã lập chỉ mục
            {!chunksLoading && chunksPage && (
              <span className="text-muted-foreground ml-2 text-sm font-normal">
                ({totalElements} đoạn)
              </span>
            )}
          </h2>
          <p className="text-muted-foreground mt-0.5 text-xs">
            Phê duyệt nguồn sẽ tự động cập nhật trạng thái phê duyệt cho tất cả đoạn của nguồn đó.
          </p>
        </div>

        <div className="flex items-center gap-2">
          <span className="text-muted-foreground text-xs whitespace-nowrap">Lọc theo nguồn:</span>
          {sourcesLoading ? (
            <Skeleton className="h-8 w-48" />
          ) : (
            <Popover open={comboOpen} onOpenChange={setComboOpen}>
              <PopoverTrigger asChild>
                <Button
                  variant="outline"
                  role="combobox"
                  className="h-8 w-56 justify-between px-3 text-xs font-normal"
                >
                  <span className="truncate">
                    {selectedSourceId
                      ? (sourcesPage?.content.find((s) => s.id === selectedSourceId)?.title ??
                        'Tất cả nguồn')
                      : 'Tất cả nguồn'}
                  </span>
                  <ChevronsUpDown className="ml-2 h-3.5 w-3.5 shrink-0 opacity-50" />
                </Button>
              </PopoverTrigger>
              <PopoverContent className="w-72 p-0">
                <Command>
                  <CommandInput placeholder="Tìm nguồn..." className="h-8 text-xs" />
                  <CommandList>
                    <CommandEmpty>Không tìm thấy nguồn.</CommandEmpty>
                    <CommandGroup>
                      <CommandItem
                        value={ALL_SOURCES}
                        onSelect={() => {
                          handleSourceChange(ALL_SOURCES);
                          setComboOpen(false);
                        }}
                      >
                        <Check
                          className={cn(
                            'mr-2 h-3.5 w-3.5',
                            !selectedSourceId ? 'opacity-100' : 'opacity-0',
                          )}
                        />
                        Tất cả nguồn
                      </CommandItem>
                      {sourcesPage?.content.map((src) => (
                        <CommandItem
                          key={src.id}
                          value={src.title}
                          onSelect={() => {
                            handleSourceChange(src.id);
                            setComboOpen(false);
                          }}
                        >
                          <Check
                            className={cn(
                              'mr-2 h-3.5 w-3.5',
                              selectedSourceId === src.id ? 'opacity-100' : 'opacity-0',
                            )}
                          />
                          <span className="truncate">{src.title}</span>
                        </CommandItem>
                      ))}
                    </CommandGroup>
                  </CommandList>
                </Command>
              </PopoverContent>
            </Popover>
          )}
        </div>
      </div>

      {chunksLoading ? (
        <div className="space-y-2">
          {Array.from({ length: 5 }).map((_, i) => (
            <Skeleton key={i} className="h-12 w-full" />
          ))}
        </div>
      ) : (
        <div className="rounded-md border">
          <Table>
            <TableHeader>
              {table.getHeaderGroups().map((hg) => (
                <TableRow key={hg.id}>
                  {hg.headers.map((h) => (
                    <TableHead key={h.id}>
                      {h.isPlaceholder
                        ? null
                        : flexRender(h.column.columnDef.header, h.getContext())}
                    </TableHead>
                  ))}
                </TableRow>
              ))}
            </TableHeader>
            <TableBody>
              {table.getRowModel().rows.length ? (
                table.getRowModel().rows.map((row) => (
                  <TableRow key={row.id} className="hover:bg-muted/40">
                    {row.getVisibleCells().map((cell) => (
                      <TableCell key={cell.id}>
                        {flexRender(cell.column.columnDef.cell, cell.getContext())}
                      </TableCell>
                    ))}
                  </TableRow>
                ))
              ) : (
                <TableRow>
                  <TableCell
                    colSpan={columns.length}
                    className="text-muted-foreground h-24 text-center text-sm"
                  >
                    Chưa có đoạn văn bản nào trong chỉ mục.
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </div>
      )}

      {/* Pagination */}
      {totalPages > 1 && (
        <div className="text-muted-foreground flex items-center justify-between text-xs">
          <span>
            Trang {page + 1} / {totalPages}
          </span>
          <div className="flex gap-2">
            <Button
              variant="outline"
              size="sm"
              className="h-7 px-3 text-xs"
              disabled={page === 0}
              onClick={() => setPage((p) => p - 1)}
            >
              Trước
            </Button>
            <Button
              variant="outline"
              size="sm"
              className="h-7 px-3 text-xs"
              disabled={page >= totalPages - 1}
              onClick={() => setPage((p) => p + 1)}
            >
              Sau
            </Button>
          </div>
        </div>
      )}

      <ChunkDetailSheet chunkId={selectedChunkId} onClose={() => setSelectedChunkId(null)} />
    </div>
  );
}
