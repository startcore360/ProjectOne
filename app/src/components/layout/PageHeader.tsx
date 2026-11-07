import { PageHeader as DsPageHeader, type PageHeaderProps } from "../../design-system/components/PageHeader";

export type { PageHeaderProps };

export function PageHeader(props: PageHeaderProps) {
  return <DsPageHeader {...props} />;
}

export default PageHeader;
