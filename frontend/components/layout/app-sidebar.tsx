'use client';

import { usePathname } from 'next/navigation';
import Link from 'next/link';
import { MessageSquare, Database, BookOpen, Settings } from 'lucide-react';
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
  { title: 'Quan ly nguon', href: '/sources', icon: Database },
  { title: 'Chi muc kien thuc', href: '/index', icon: BookOpen },
  { title: 'Bo tham so AI', href: '/parameters', icon: Settings },
];

export function AppSidebar() {
  const pathname = usePathname();

  return (
    <Sidebar>
      <SidebarHeader className="p-4">
        <h2 className="text-xl font-semibold">Luat Giao thong</h2>
      </SidebarHeader>
      <SidebarContent>
        {/* Chat section with thread list */}
        <SidebarGroup>
          <SidebarGroupLabel>
            <Link href="/" className="flex items-center gap-2">
              <MessageSquare className="h-4 w-4" />
              Tro chuyen
            </Link>
          </SidebarGroupLabel>
          <SidebarGroupContent>
            <ThreadList />
          </SidebarGroupContent>
        </SidebarGroup>

        <SidebarSeparator />

        {/* Admin section */}
        <SidebarGroup>
          <SidebarGroupLabel>Quan tri</SidebarGroupLabel>
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
