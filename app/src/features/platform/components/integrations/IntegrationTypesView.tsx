import { useEffect, useState, type FormEvent } from "react";
import {
  Button,
  Panel,
  StatusBadge,
  DataTable,
  SearchBox,
  Notice,
} from "../../../../design-system/components";
import { Spinner } from "../../../../components/ui";
import { PageHeader } from "../../../../components/layout/PageHeader";
import { formatTimestamp, readErrorMessage, type FetchStatus } from "../common/types";
import api from "../../../../services/axios";
import {
  CreateIntegrationTypeModal,
  type CreateIntegrationTypeDraft,
} from "./CreateIntegrationTypeModal";
import {
  EditIntegrationTypeModal,
  type EditIntegrationTypeDraft,
  type IntegrationTypeItem,
  type IntegrationTypeStatusValue,
} from "./EditIntegrationTypeModal";

function parseProviders(value: string): string[] {
  return value
    .split(",")
    .map((p) => p.trim())
    .filter(Boolean);
}

export function IntegrationTypesView() {
  const [status, setStatus] = useState<FetchStatus>("loading");
  const [items, setItems] = useState<IntegrationTypeItem[]>([]);
  const [errorMessage, setErrorMessage] = useState("");
  const [query, setQuery] = useState("");
  const [message, setMessage] = useState("");

  const [showCreate, setShowCreate] = useState(false);
  const [createDraft, setCreateDraft] = useState<CreateIntegrationTypeDraft>({
    integrationTypeCode: "",
    description: "",
    allowedProviders: "",
  });
  const [createError, setCreateError] = useState("");
  const [creating, setCreating] = useState(false);

  const [editing, setEditing] = useState<IntegrationTypeItem | null>(null);
  const [editDraft, setEditDraft] = useState<EditIntegrationTypeDraft>({
    description: "",
    status: "ALLOWED" as IntegrationTypeStatusValue,
    allowedProviders: "",
  });
  const [editError, setEditError] = useState("");
  const [saving, setSaving] = useState(false);

  const load = () =>
    api
      .get("/platform/integration-types")
      .then((res) => {
        setItems(res.data ?? []);
        setStatus("success");
      })
      .catch((err) => {
        setErrorMessage(readErrorMessage(err, "Could not load integration types."));
        setStatus("error");
      });

  useEffect(() => {
    load();
  }, []);

  const filtered = items.filter((i) =>
    (i.integrationTypeCode + i.description).toLowerCase().includes(query.toLowerCase())
  );

  const submitCreate = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setCreateError("");
    setCreating(true);
    api
      .post("/platform/integration-types", {
        integrationTypeCode: createDraft.integrationTypeCode,
        description: createDraft.description,
        allowedProviders: parseProviders(createDraft.allowedProviders),
      })
      .then(() => {
        setShowCreate(false);
        setCreateDraft({ integrationTypeCode: "", description: "", allowedProviders: "" });
        setMessage("Integration type created.");
        return load();
      })
      .catch((err) => setCreateError(readErrorMessage(err, "Could not create integration type.")))
      .finally(() => setCreating(false));
  };

  const openEdit = (item: IntegrationTypeItem) => {
    setMessage("");
    setEditError("");
    setEditing(item);
    setEditDraft({
      description: item.description,
      status: item.status,
      allowedProviders: item.allowedProviders.join(", "),
    });
  };

  const submitEdit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!editing) return;
    setEditError("");
    setSaving(true);
    api
      .patch(`/platform/integration-types/${editing.integrationTypeId}`, {
        description: editDraft.description,
        status: editDraft.status,
        allowedProviders: parseProviders(editDraft.allowedProviders),
      })
      .then(() => {
        setEditing(null);
        setMessage("Integration type updated.");
        return load();
      })
      .catch((err) => setEditError(readErrorMessage(err, "Could not update integration type.")))
      .finally(() => setSaving(false));
  };

  return (
    <div className="platform-page">
      <PageHeader
        title="Integration types"
        description="The catalogue of integration types a tenant may enable (e.g. SMS, payments). Type catalogue only - never a tenant's own keys or webhooks."
        actions={
          <Button
            onClick={() => {
              setMessage("");
              setCreateError("");
              setShowCreate(true);
            }}
          >
            Add integration type
          </Button>
        }
      />
      {message && (
        <div className="platform-inline-notice" role="status">
          {message}
        </div>
      )}
      {status === "error" && <Notice tone="danger">Could not load integration types: {errorMessage}</Notice>}
      <Panel title="Allowed integration types" description="platform.integration_type">
        <div className="platform-toolbar">
          <SearchBox value={query} onChange={setQuery} placeholder="Search by code or description" />
        </div>
        {status === "loading" ? (
          <div className="platform-empty-inline">
            <Spinner label="Loading integration types" />
          </div>
        ) : (
          <DataTable
            headers={["Code", "Description", "Status", "Allowed providers", "Updated", ""]}
            empty={!filtered.length}
            emptyMessage={
              status === "error" ? "Integration types could not be loaded." : "No integration types defined yet."
            }
          >
            {filtered.map((item) => (
              <tr key={item.integrationTypeId}>
                <td>
                  <strong>{item.integrationTypeCode}</strong>
                </td>
                <td>{item.description}</td>
                <td>
                  <StatusBadge value={item.status === "ALLOWED" ? "Allowed" : "Disallowed"} />
                </td>
                <td>{item.allowedProviders.length ? item.allowedProviders.join(", ") : "—"}</td>
                <td>{formatTimestamp(item.updatedAt)}</td>
                <td>
                  <Button variant="secondary" size="sm" onClick={() => openEdit(item)}>
                    Edit
                  </Button>
                </td>
              </tr>
            ))}
          </DataTable>
        )}
      </Panel>

      <CreateIntegrationTypeModal
        isOpen={showCreate}
        onClose={() => setShowCreate(false)}
        onSubmit={submitCreate}
        createDraft={createDraft}
        setCreateDraft={setCreateDraft}
        creating={creating}
        createError={createError}
      />

      <EditIntegrationTypeModal
        isOpen={Boolean(editing)}
        onClose={() => setEditing(null)}
        onSubmit={submitEdit}
        editing={editing}
        editDraft={editDraft}
        setEditDraft={setEditDraft}
        saving={saving}
        editError={editError}
      />
    </div>
  );
}

// Export alias
export const IntegrationTypesPage = IntegrationTypesView;
