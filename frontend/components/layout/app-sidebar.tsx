'use client';

import { usePathname } from 'next/navigation';
import Link from 'next/link';
import { MessageSquare, Database, BookOpen, Settings, ShieldCheck } from 'lucide-react';
import {
  Sidebar,
  SidebarContent,
  SidebarGroup,
  SidebarGroupContent,
  SidebarGroupLabel,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  SidebarHeader,
  SidebarSeparator,
} from '@/components/ui/sidebar';
import { ThreadList } from '@/components/chat/thread-list';

export const adminNavItems = [
  { title: 'Quản lý nguồn', href: '/sources', icon: Database },
  { title: 'Chỉ mục kiến thức', href: '/index', icon: BookOpen },
  { title: 'Bộ tham số AI', href: '/parameters', icon: Settings },
  { title: 'Chính sách tin cậy', href: '/trust-policy', icon: ShieldCheck },
];

export function AppSidebar() {
  const pathname = usePathname();

  return (
    <Sidebar>
      <SidebarHeader className="p-4">
        <h2 className="text-xl font-semibold">Luật Giao thông</h2>
      </SidebarHeader>
      <SidebarContent>
        {/* Phần chat với danh sách hội thoại */}
        <SidebarGroup>
          <SidebarGroupLabel>
            <Link href="/" className="flex items-center gap-2">
              <MessageSquare className="h-4 w-4" />
              Trò chuyện
            </Link>
          </SidebarGroupLabel>
          <SidebarGroupContent>
            <ThreadList />
          </SidebarGroupContent>
        </SidebarGroup>

        <SidebarSeparator />

        {/* Phần quản trị */}
        <SidebarGroup>
          <SidebarGroupLabel>Quản trị</SidebarGroupLabel>
          <SidebarGroupContent>
            <SidebarMenu>
              {adminNavItems.map((item) => (
                <SidebarMenuItem key={item.href}>
                  <SidebarMenuButton
                    isActive={pathname.startsWith(item.href)}
                    render={<Link href={item.href} />}
                  >
                    <item.icon className="h-4 w-4" />
                    <span>{item.title}</span>
                  </SidebarMenuButton>
                </SidebarMenuItem>
              ))}
            </SidebarMenu>
          </SidebarGroupContent>
        </SidebarGroup>
      </SidebarContent>
    </Sidebar>
  );
}
