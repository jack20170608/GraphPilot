import { ConsoleLayout } from "@/components/layout/console-layout";

export default function ConsoleGroupLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return <ConsoleLayout>{children}</ConsoleLayout>;
}
