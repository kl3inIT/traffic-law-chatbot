import { IndexCards } from '@/components/admin/index/index-cards';
import { IndexSourcesTable } from '@/components/admin/index/index-sources-table';
import { IndexChunkTable } from '@/components/admin/index/index-chunk-table';
import { Tabs, TabsList, TabsTrigger, TabsContent } from '@/components/ui/tabs';

export default function IndexPage() {
  return (
    <div className="space-y-6 p-6">
      <IndexCards />

      <Tabs defaultValue="sources">
        <TabsList>
          <TabsTrigger value="sources">Nguồn đang index</TabsTrigger>
          <TabsTrigger value="chunks">Đoạn văn bản / Vector</TabsTrigger>
        </TabsList>

        <TabsContent value="sources" className="pt-4">
          <IndexSourcesTable />
        </TabsContent>

        <TabsContent value="chunks" className="pt-4">
          <IndexChunkTable />
        </TabsContent>
      </Tabs>
    </div>
  );
}
