'use client';

import { useState } from 'react';
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
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { Skeleton } from '@/components/ui/skeleton';
import { ChunkDetailSheet } from './chunk-detail-sheet';
import { useChunks } from '@/hooks/use-index';
import { useSources } from '@/hooks/use-sources';
import type { ChunkSummaryResponse } from '@/types/api';

const ALL_SOURCES = '__all__';

function truncateUuid(id: string): string {
  return id.length > 8 ? `${id.slice(0, 8)}…` : id;
}

function EmbeddingPreviewBadge({ values }: { values: number[] | null }) {
  if (!values || values.length === 0) {
    return <span className="text-muted-foreground text-xs italic">—</span>;
  }
  const preview = values
    .slice(0, 5)
    .map((v) => v.toFixed(3))
    .join(', ');
  return (
    <span className="text-muted-foreground font-mono text-xs">
      [{preview}
      {values.length > 5 ? ', …' : ''}]
    </span>
  );
}

function ApprovalBadge({ state }: { state: string | null }) {
  if (!state) return null;
  const cls =
    state === 'APPROVED'
      ? 'bg-green-100 text-green-800'
      : state === 'PENDING'
        ? 'bg-yellow-100 text-yellow-800'
        : 'bg-gray-100 text-gray-600';
  const label = state === 'APPROVED' ? 'Đã duyệt' : state === 'PENDING' ? 'Chờ duyệt' : state;
  return <Badge className={`text-xs ${cls}`}>{label}</Badge>;
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
      accessorKey: 'contentPreview',
      header: 'Nội dung',
      cell: ({ row }) => (
        <div className="text-foreground line-clamp-2 max-w-[260px] text-xs leading-relaxed">
          {row.original.contentPreview ?? (
            <span className="text-muted-foreground italic">(trống)</span>
          )}
        </div>
      ),
    },
    {
      accessorKey: 'approvalState',
      header: 'Trạng thái',
      cell: ({ row }) => (
        <div className="flex flex-col gap-1">
          <ApprovalBadge state={row.original.approvalState} />
          <div className="flex gap-1">
            {row.original.trusted === 'true' && (
              <Badge variant="secondary" className="text-xs">
                Tin cậy
              </Badge>
            )}
            {row.original.active === 'true' && (
              <Badge variant="secondary" className="text-xs">
                Hoạt động
              </Badge>
            )}
          </div>
        </div>
      ),
    },
    {
      accessorKey: 'embeddingPreview',
      header: 'Véc-tơ',
      cell: ({ row }) => (
        <div className="flex flex-col gap-1">
          <EmbeddingPreviewBadge values={row.original.embeddingPreview} />
          {row.original.vectorDimension > 0 && (
            <span className="text-muted-foreground text-xs">{row.original.vectorDimension}d</span>
          )}
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

  const { data: sourcesPage, isLoading: sourcesLoading } = useSources();
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
        <h2 className="text-base font-semibold">
          Đoạn văn bản đã lập chỉ mục
          {!chunksLoading && chunksPage && (
            <span className="text-muted-foreground ml-2 text-sm font-normal">
              ({totalElements} đoạn)
            </span>
          )}
        </h2>

        <div className="flex items-center gap-2">
          <span className="text-muted-foreground text-xs whitespace-nowrap">Lọc theo nguồn:</span>
          {sourcesLoading ? (
            <Skeleton className="h-8 w-40" />
          ) : (
            <Select value={selectedSourceId ?? ALL_SOURCES} onValueChange={handleSourceChange}>
              <SelectTrigger className="h-8 w-52 text-xs">
                <SelectValue placeholder="Tất cả nguồn" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value={ALL_SOURCES}>Tất cả nguồn</SelectItem>
                {sourcesPage?.content.map((src) => (
                  <SelectItem key={src.id} value={src.id}>
                    <span className="max-w-[200px] truncate">{src.title}</span>
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          )}
        </div>
      </div>

      {chunksLoading ? (
        <div className="space-y-2">
          {Array.from({ length: 5 }).map((_, i) => (
            <Skeleton key={i} className="h-14 w-full" />
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
