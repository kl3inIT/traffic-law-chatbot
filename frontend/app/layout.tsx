import type { Metadata } from "next";
import { Inter } from "next/font/google";
import { Providers } from "@/components/layout/providers";
import { SidebarProvider } from "@/components/ui/sidebar";
import { AppSidebar } from "@/components/layout/app-sidebar";
import "./globals.css";

const inter = Inter({ subsets: ["latin", "vietnamese"] });

export const metadata: Metadata = {
  title: "Chatbot Luat Giao thong Viet Nam",
  description: "Tro ly AI ve luat giao thong Viet Nam",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="vi" className="h-full antialiased">
      <body className={`${inter.className} min-h-full flex flex-col`}>
        <Providers>
          <SidebarProvider defaultOpen={true}>
            <AppSidebar />
            <main className="flex-1 overflow-hidden">{children}</main>
          </SidebarProvider>
        </Providers>
      </body>
    </html>
  );
}
