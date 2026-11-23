import { Modal,Button,Notice } from "../../../design-system/components";
type Props={open:boolean;busy?:boolean;onClose:()=>void;onApprove:()=>void};
export default function MeritApprovalModal({open,busy=false,onClose,onApprove}:Props){
 return <Modal isOpen={open} onClose={onClose} title="Approve master merit list" description="Final approval is a controlled, individual action.">
 <Notice tone="warning">Rule C12: approval is non-delegable and cannot be bulk-approved. Only a substantive Principal, Vice-Chancellor or Product Owner may approve.</Notice>
 <p>Confirm that you have reviewed the provisional ranking and quota allocations.</p>
 <div className="app-inline-actions"><Button variant="secondary" onClick={onClose}>Cancel</Button><Button busy={busy} onClick={onApprove}>Approve merit list</Button></div>
 </Modal>;
}
