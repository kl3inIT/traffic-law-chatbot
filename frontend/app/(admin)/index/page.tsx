import { IndexCards } from '@/components/admin/index/index-cards';
import { IndexChunkTable } from '@/components/admin/index/index-chunk-table';

export default function IndexPage() {
  return (
    <div className="space-y-6 p-6">
      <IndexCards />
      <IndexChunkTable />
    </div>
  );
}
