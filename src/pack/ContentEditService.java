/* ***** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * The Original Code is part of dcm4che, an implementation of DICOM(TM) in
 * Java(TM), available at http://sourceforge.net/projects/dcm4che.
 *
 * The Initial Developer of the Original Code is
 * TIANI Medgraph AG.
 * Portions created by the Initial Developer are Copyright (C) 2003-2005
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 * Gunter Zeilinger <gunter.zeilinger@tiani.com>
 * Franz Willer <franz.willer@gwi-ag.com>
 *
 * Alternatively, the contents of this file may be used under the terms of
 * either the GNU General Public License Version 2 or later (the "GPL"), or
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisions of the GPL or the LGPL are applicable instead
 * of those above. If you wish to allow use of your version of this file only
 * under the terms of either the GPL or the LGPL, and not to allow others to
 * use your version of this file under the terms of the MPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any one of the MPL, the GPL or the LGPL.
 *
 * ***** END LICENSE BLOCK ***** */
package org.dcm4chee.web.service.contentedit;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.Notification;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import javax.naming.InitialContext;
import javax.xml.transform.Templates;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamSource;

import org.dcm4che2.audit.message.AuditEvent;
import org.dcm4che2.data.BasicDicomObject;
import org.dcm4che2.data.DicomElement;
import org.dcm4che2.data.DicomObject;
import org.dcm4che2.data.PersonName;
import org.dcm4che2.data.Tag;
import org.dcm4che2.data.UID;
import org.dcm4che2.data.VR;
import org.dcm4che2.util.StringUtils;
import org.dcm4che2.util.UIDUtils;
import org.dcm4chee.archive.common.Availability;
import org.dcm4chee.archive.common.PrivateTag;
import org.dcm4chee.archive.entity.Code;
import org.dcm4chee.archive.entity.File;
import org.dcm4chee.archive.entity.Instance;
import org.dcm4chee.archive.entity.MPPS;
import org.dcm4chee.archive.entity.MWLItem;
import org.dcm4chee.archive.entity.Patient;
import org.dcm4chee.archive.entity.Series;
import org.dcm4chee.archive.entity.Study;
import org.dcm4chee.web.common.util.Auditlog;
import org.dcm4chee.web.common.util.FileUtils;
import org.dcm4chee.web.dao.common.DicomEditLocal;
import org.dcm4chee.web.dao.folder.MppsToMwlLinkLocal;
import org.dcm4chee.web.dao.util.CoercionUtil;
import org.dcm4chee.web.dao.vo.EntityTree;
import org.dcm4chee.web.dao.vo.MppsToMwlLinkResult;
import org.dcm4chee.web.service.common.DicomActionNotification;
import org.dcm4chee.web.service.common.FileImportOrder;
import org.dcm4chee.web.service.common.XSLTUtils;
import org.dcm4chee.web.service.common.delegate.TemplatesDelegate;
import org.jboss.system.ServiceMBeanSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author franz.willer@gmail.com
 * @version $Revision$ $Date$
 * @since Jan 29, 2009
 */
public class ContentEditService extends ServiceMBeanSupport {

    private static final String STRING = String.class.getName();

    private static Logger log = LoggerFactory.getLogger(ContentEditService.class);
    
    private static final String NONE ="NONE";
    private static final String MWL2STORE_XSL = "mwl-cfindrsp2cstorerq.xsl";

    private static final int[] EXCLUDE_PPS_ATTRS = new int[]{
        Tag.ReferencedPerformedProcedureStepSequence, 
        Tag.PerformedProcedureStepStartDate, Tag.PerformedProcedureStepStartTime};

    private Code rejectNoteCode = new Code();

    private DicomEditLocal dicomEdit;
    private MppsToMwlLinkLocal mpps2mwl;

    private boolean forceNewRejNoteStudyIUID;
    
    private ObjectName rejNoteServiceName;
    private ObjectName ianScuServiceName;
    private ObjectName moveScuServiceName;
    private ObjectName storeScpServiceName;
    private ObjectName attrModScuServiceName;

    private boolean processIAN;
    private boolean processRejNote;
    private boolean dcm14Stylesheet;
    
    private String addMwlAttrsToMppsXsl;
    
    private String[] forwardModifiedToAETs;
    
    private String modifyingSystem;
    private String modifyReason;
        
    private static final TransformerFactory tf = TransformerFactory.newInstance();
    protected TemplatesDelegate templates = new TemplatesDelegate(this);
    private String dcm2To14TplName, dcm14To2TplName;
    private Templates dcm2To14Tpl, dcm14To2Tpl;
    
    public String getUIDRoot() {
        return UIDUtils.getRoot();
    }
    
    public void setUIDRoot(String root) {
        UIDUtils.setRoot(root);
    }
    
    public String getRejectionNoteCode() {
        return rejectNoteCode.toString()+"\r\n";
    }

    public void setRejectionNoteCode(String code) {
        rejectNoteCode = new Code(code);
    }

    public boolean isProcessIAN() {
        return processIAN;
    }

    public void setProcessIAN(boolean processIAN) {
        this.processIAN = processIAN;
    }

    public boolean isProcessRejNote() {
        return processRejNote;
    }

    public void setProcessRejNote(boolean processRejNote) {
        this.processRejNote = processRejNote;
    }
    
    public String getForwardModifiedToAETs() {
        return forwardModifiedToAETs == null ? NONE : StringUtils.join(forwardModifiedToAETs, '\\');
    }

    public void setForwardModifiedToAETs(String aets) {
        this.forwardModifiedToAETs = NONE.equals(aets) ? null : StringUtils.split(aets, '\\');
    }

    public boolean isForceNewRejNoteStudyIUID() {
        return forceNewRejNoteStudyIUID;
    }

    public void setForceNewRejNoteStudyIUID(boolean forceNewRejNoteStudyIUID) {
        this.forceNewRejNoteStudyIUID = forceNewRejNoteStudyIUID;
    }

    public String getModifyingSystem() {
        return modifyingSystem;
    }

    public void setModifyingSystem(String modifyingSystem) {
        this.modifyingSystem = modifyingSystem;
    }

    public String getModifyReason() {
        return modifyReason;
    }

    public void setModifyReason(String modifyReason) {
        this.modifyReason = modifyReason;
    }

    public final String getCoerceConfigDir() {
        return templates.getConfigDir();
    }

    public final void setCoerceConfigDir(String path) {
        templates.setConfigDir(path);
    }

    public ObjectName getRejectionNoteServiceName() {
        return rejNoteServiceName;
    }
    public void setRejectionNoteServiceName(ObjectName name) {
        this.rejNoteServiceName = name;
    }

    public ObjectName getIANScuServiceName() {
        return ianScuServiceName;
    }
    public void setIANScuServiceName(ObjectName name) {
        this.ianScuServiceName = name;
    }

    public void setMoveScuServiceName(ObjectName name) {
        this.moveScuServiceName = name;
    }
    public ObjectName getMoveScuServiceName() {
        return moveScuServiceName;
    }

    public ObjectName getStoreScpServiceName() {
        return storeScpServiceName;
    }

    public void setStoreScpServiceName(ObjectName name) {
        storeScpServiceName = name;
    }
    
    public ObjectName getAttrModificationScuServiceName() {
        return this.attrModScuServiceName;
    }

    public void setAttrModificationScuServiceName(ObjectName name) {
        attrModScuServiceName = name;
    }
    
    public final ObjectName getTemplatesServiceName() {
        return templates.getTemplatesServiceName();
    }

    public final void setTemplatesServiceName(ObjectName serviceName) {
        templates.setTemplatesServiceName(serviceName);
    }

    public String getAddMwlAttrsToMppsXsl() {
        return addMwlAttrsToMppsXsl == null ? NONE : addMwlAttrsToMppsXsl;
    }

    public void setAddMwlAttrsToMppsXsl(String addMwlAttrToMppsXsl) {
        this.addMwlAttrsToMppsXsl = NONE.equals(addMwlAttrToMppsXsl) ? null : addMwlAttrToMppsXsl;
    }

    public boolean isDcm14Stylesheet() {
        return dcm14Stylesheet;
    }

    public void setDcm14Stylesheet(boolean dcm14Stylesheet) {
        this.dcm14Stylesheet = dcm14Stylesheet;
    }

    public String getDcm2To14Tpl() {
        return dcm2To14TplName;
    }

    public void setDcm2To14Tpl(String name) throws TransformerConfigurationException, MalformedURLException {
        new URL(name);
        dcm2To14Tpl = tf.newTemplates(new StreamSource(name));
        dcm2To14TplName = name;
    }

    public String getDcm14To2Tpl() {
        return dcm14To2TplName;
    }

    public void setDcm14To2Tpl(String name) throws MalformedURLException, TransformerConfigurationException {
        new URL(name);
        dcm14To2Tpl = tf.newTemplates(new StreamSource(name));
        dcm14To2TplName = name;
    }

    public DicomObject moveInstanceToTrash(String iuid) throws InstanceNotFoundException, MBeanException, ReflectionException {
        DicomObject[] rejNotes = processInstancesDeleted(lookupDicomEditLocal().moveInstanceToTrash(iuid), "Referenced Series of deleted Instances:");
        return rejNotes == null ? null : rejNotes[0];
    }

    public DicomObject[] moveInstancesToTrash(long[] pks) throws InstanceNotFoundException, MBeanException, ReflectionException {
        return processInstancesDeleted(lookupDicomEditLocal().moveInstancesToTrash(pks), "Referenced Series of deleted Instances:");
    }

    public DicomObject moveSeriesToTrash(String iuid) throws InstanceNotFoundException, MBeanException, ReflectionException {
        DicomObject[] rejNotes = processInstancesDeleted(lookupDicomEditLocal().moveSeriesToTrash(iuid), "Deleted Series:");
        return rejNotes == null ? null : rejNotes[0];
    }
    
    public DicomObject[] moveSeriesToTrash(long[] pks) throws InstanceNotFoundException, MBeanException, ReflectionException {
        return processInstancesDeleted(lookupDicomEditLocal().moveSeriesToTrash(pks), "Deleted Series:");
    }

    public DicomObject[] moveSeriesOfPpsToTrash(long[] pks) throws InstanceNotFoundException, MBeanException, ReflectionException {
        return processInstancesDeleted(lookupDicomEditLocal().moveSeriesOfPpsToTrash(pks), "Deleted Series:");
    }
    
    public List<MPPS> deletePps(long[] ppsPks) {
        List<MPPS> mppss = lookupDicomEditLocal().deletePps(ppsPks);
        for (MPPS mpps : mppss) {
            Auditlog.logProcedureRecord(AuditEvent.ActionCode.UPDATE, true, mpps.getPatient().getAttributes(), 
                mpps.getAttributes().getString(new int[]{Tag.ScheduledStepAttributesSequence,0,Tag.StudyInstanceUID}),
                mpps.getAccessionNumber(), "MPPS deleted");
        }
        return mppss;
    }
    
    public DicomObject moveStudyToTrash(String iuid) throws InstanceNotFoundException, MBeanException, ReflectionException {
        DicomObject[] rejNotes = processStudyDeleted(lookupDicomEditLocal().moveStudyToTrash(iuid));
        return rejNotes == null ? null : rejNotes[0];
    }
    public DicomObject[] moveStudiesToTrash(long[] pks) throws InstanceNotFoundException, MBeanException, ReflectionException {
        return processStudyDeleted(lookupDicomEditLocal().moveStudiesToTrash(pks));
    }

    public DicomObject[] movePatientToTrash(String pid, String issuer) throws InstanceNotFoundException, MBeanException, ReflectionException {
        EntityTree entityTree = lookupDicomEditLocal().movePatientToTrash(pid, issuer);
        DicomObject[] rejNotes = processStudyDeleted(entityTree);
        logPatientDeleted(entityTree);
        return rejNotes;
    }
    public DicomObject[] movePatientsToTrash(long[] pks) throws InstanceNotFoundException, MBeanException, ReflectionException {
        EntityTree entityTree = lookupDicomEditLocal().movePatientsToTrash(pks);
        DicomObject[] rejNotes = processStudyDeleted(entityTree);
        logPatientDeleted(entityTree);
        return rejNotes;
    }

    public int moveInstancesToSeries(long[] instPks, long seriesPk) throws InstanceNotFoundException, MBeanException, ReflectionException {
        DicomObject targetAttrs = lookupDicomEditLocal().getCompositeObjectforSeries(seriesPk);
        EntityTree entityTree = lookupDicomEditLocal().moveInstancesToTrash(instPks);
        processAfterMoveEntities(entityTree, targetAttrs, null);
        return entityTree.getAllInstances().size();
    }

    public int moveInstanceToSeries(String sopIUID, String seriesIUID) throws InstanceNotFoundException, MBeanException, ReflectionException {
        DicomObject targetAttrs = lookupDicomEditLocal().getCompositeObjectforSeries(seriesIUID);
        EntityTree entityTree = lookupDicomEditLocal().moveInstanceToTrash(sopIUID);
        processAfterMoveEntities(entityTree, targetAttrs, null);
        return entityTree.getAllInstances().size();
    }
    
    public int moveSeriesToStudy(long[] seriesPks, long studyPk) throws InstanceNotFoundException, MBeanException, ReflectionException {
        DicomObject targetAttrs = lookupDicomEditLocal().getCompositeObjectforStudy(studyPk);
        EntityTree entityTree = lookupDicomEditLocal().moveSeriesToTrash(seriesPks);
        processAfterMoveEntities(entityTree, targetAttrs, EXCLUDE_PPS_ATTRS);
        return entityTree.getAllInstances().size();
    }

    public int moveSeriesToStudy(String seriesIUID, String studyIUID) throws InstanceNotFoundException, MBeanException, ReflectionException {
        DicomObject targetAttrs = lookupDicomEditLocal().getCompositeObjectforStudy(studyIUID);
        EntityTree entityTree = lookupDicomEditLocal().moveSeriesToTrash(seriesIUID);
        processAfterMoveEntities(entityTree, targetAttrs, EXCLUDE_PPS_ATTRS);
        return entityTree.getAllInstances().size();
    }

    public int moveStudiesToPatient(long[] studyPks, long patPk) throws InstanceNotFoundException, MBeanException, ReflectionException {
        if ( studyPks == null || studyPks.length < 1) {
            return 0;
        }
        EntityTree entityTree = lookupDicomEditLocal().moveStudiesToPatient(studyPks, patPk);
        if (!entityTree.isEmpty()) {
            DicomObject kos = getRejectionNotes(entityTree)[0];
            Auditlog.logInstancesAccessed(AuditEvent.ActionCode.UPDATE, true, kos, true, "Studies moved to patient:");
            scheduleMoveStudyToPatient(entityTree);
        }
        return entityTree.getAllInstances().size();
    }

    private void scheduleMoveStudyToPatient(EntityTree entityTree) {
        try {
            DicomObject obj = new BasicDicomObject();
            obj.putString(Tag.QueryRetrieveLevel, VR.CS, "PATIENT");
            Set<Study> studies = entityTree.getEntityTreeMap().values().iterator().next().keySet();
            String[] suids = new String[studies.size()];
            int i = 0;
            for (Iterator<Study> it = studies.iterator() ; it.hasNext() ; ) {
                suids[i++] = it.next().getStudyInstanceUID();
            }
            studies.iterator().next().getPatient().getAttributes().copyTo(obj);
            obj.putStrings(Tag.StudyInstanceUID, VR.UI, suids);
            log.info("Schedule PATIENT level Attributes Modification Notification (Move Study To Patient)");
            server.invoke(attrModScuServiceName, "scheduleModification", 
                    new Object[]{obj}, new String[]{DicomObject.class.getName()});
        } catch (Exception e) {
            log.error("Scheduling Attributes Modification Notification (Move Study To Patient) failed!", e);
        }
    }
    public int moveStudyToPatient(String studyIUID, String patId, String issuer) throws InstanceNotFoundException, MBeanException, ReflectionException {
        EntityTree entityTree = lookupDicomEditLocal().moveStudyToPatient(studyIUID, patId, issuer);
        if (!entityTree.isEmpty()) {
            DicomObject kos = getRejectionNotes(entityTree)[0];
            Auditlog.logInstancesAccessed(AuditEvent.ActionCode.UPDATE, true, kos, true, "Studies moved to patient:");
            scheduleMoveStudyToPatient(entityTree);
        }
        return entityTree.getAllInstances().size();
    }
    
    public Study createStudy(DicomObject studyAttrs, long patPk) {
        return lookupDicomEditLocal().createStudy(studyAttrs, patPk);
    }
    public Study updateStudy(Study study) {
        return lookupDicomEditLocal().updateStudy(study);
    }
    public Series createSeries(DicomObject seriesAttrs, long studyPk) {
        return lookupDicomEditLocal().createSeries(seriesAttrs, studyPk);
    }
    public Series updateSeries(Series series) {
        return lookupDicomEditLocal().updateSeries(series);
    }

    private void processAfterMoveEntities(EntityTree entityTree, DicomObject targetAttrs, int[] excludeTagsForImport)
        throws InstanceNotFoundException, MBeanException,ReflectionException {
        if (!entityTree.isEmpty()) { 
            processRejectionNotes(entityTree, false, "Deleted Instances for move entities:");
            processIANs(entityTree, Availability.UNAVAILABLE);
            importFiles(entityTree, targetAttrs, excludeTagsForImport);
        }
    }

    private void importFiles(EntityTree entityTree, DicomObject targetAttrs, int[] excludeTags) {
        FileImportOrder order = new FileImportOrder();
        DicomObject headerAttrs, studyAttrs, seriesAttrs;
        for ( Map<Study, Map<Series, Set<Instance>>> studies : entityTree.getEntityTreeMap().values() ) {
            for (Map.Entry<Study, Map<Series, Set<Instance>>> entry : studies.entrySet() ) {
                if (targetAttrs.containsValue(Tag.StudyInstanceUID)) {
                    studyAttrs = null;
                } else {
                    studyAttrs = entry.getKey().getAttributes(false);
                }
                for (Map.Entry<Series, Set<Instance>> seriesEntry : entry.getValue().entrySet()) {
                    if (targetAttrs.containsValue(Tag.SeriesInstanceUID)) {
                        seriesAttrs = null;
                    } else {
                        seriesAttrs = seriesEntry.getKey().getAttributes(false);
                        seriesAttrs.putString(Tag.SeriesInstanceUID, VR.UI, UIDUtils.createUID());
                        seriesAttrs.putString(seriesAttrs.resolveTag(PrivateTag.CallingAET, PrivateTag.CreatorID), 
                                VR.AE, seriesEntry.getKey().getSourceAET());
                    }
                    for ( Instance i : seriesEntry.getValue()) {
                        if ( i.getFiles().size() < 1)
                            continue;
                        headerAttrs = new BasicDicomObject();
                        targetAttrs.copyTo(headerAttrs);
                        if ( studyAttrs != null) {
                            studyAttrs.copyTo(headerAttrs);
                        }
                        if ( seriesAttrs != null) {
                            seriesAttrs.copyTo(headerAttrs);
                        }
                        i.getAttributes(false).copyTo(headerAttrs);
                        headerAttrs.putString(Tag.SOPInstanceUID, VR.UI, UIDUtils.createUID());
                        DicomObject importHeader = headerAttrs.exclude(excludeTags);
                        for ( File f :  i.getFiles()) {
                            order.addFile(f, importHeader);
                        }
                    }
                }
            }
        }
        importFiles(order);
    }
    
    private void importFiles(FileImportOrder order) {
        log.info("import Files:"+order);
        try {
            server.invoke(storeScpServiceName, "importFile", 
                new Object[]{order}, new String[]{FileImportOrder.class.getName()});
        } catch (Throwable t) {
            log.error("Import files failed! order:"+order);
        }
    }

    public MppsToMwlLinkResult linkMppsToMwl(long[] mppsPks, long mwlPk, String system, String reason) throws InstanceNotFoundException, MBeanException, ReflectionException {
        MppsToMwlLinkResult result = lookupMppsToMwlLinkLocal().linkMppsToMwl(mppsPks, mwlPk, 
                emptyAsDefault(system, modifyingSystem), emptyAsDefault(reason, modifyReason));
        doAfterLinkMppsToMwl(result);
        return result;
    }
    public MppsToMwlLinkResult linkMppsToMwl(long[] mppsPks, DicomObject mwlAttrs, Patient pat, String system, String reason) throws InstanceNotFoundException, MBeanException, ReflectionException {
        MppsToMwlLinkResult result = lookupMppsToMwlLinkLocal().linkMppsToMwl(mppsPks, mwlAttrs, pat,
                emptyAsDefault(system, modifyingSystem), emptyAsDefault(reason, modifyReason));
        doAfterLinkMppsToMwl(result);
        return result;
    }

    public void linkMppsToMwl(String mppsIUID, String rpId, String spsId, String system, String reason) throws InstanceNotFoundException, MBeanException, ReflectionException {
        MppsToMwlLinkResult result = lookupMppsToMwlLinkLocal().linkMppsToMwl(mppsIUID, rpId, spsId, 
                emptyAsDefault(system, modifyingSystem), emptyAsDefault(reason, modifyReason));
        doAfterLinkMppsToMwl(result);
    }
    
    public List<Patient> selectPatient(DicomObject patAttrs) {
        return lookupMppsToMwlLinkLocal().selectOrCreatePatient(patAttrs);
    }

    
    private String emptyAsDefault(String value, String def) {
        return value == null || value.trim().length() < 1 ? def : value;
    }
    
    private void doAfterLinkMppsToMwl(MppsToMwlLinkResult result) throws InstanceNotFoundException, MBeanException, ReflectionException {
        log.info("MppsToMwlLinkResult:"+result);
        logMppsLinkRecord(result);
        Map<String,DicomObject> fwdIANs = updateSeriesAttributes(result);
        if (this.addMwlAttrsToMppsXsl != null) {
            addMwlAttrs2Mpps(result);
        }
        this.sendJMXNotification(result);
        log.info("MppsToMwlLinkResult: studiesToMove:"+result.getStudiesToMove().size());
        if (result.getStudiesToMove().size() > 0) {
            Patient pat = result.getMwl().getPatient();
            log.info("Patient of some MPPS are not identical to patient of MWL! Move studies to Patient of MWL:"+
                    pat.getPatientID());
            long[] studyPks = new long[result.getStudiesToMove().size()];
            int i = 0;
            for ( Study s : result.getStudiesToMove()) {
                studyPks[i++] = s.getPk();
            }
            this.moveStudiesToPatient(studyPks, pat.getPk());
        }
        log.debug("forwardModifiedToAETs:", forwardModifiedToAETs);
        log.debug("fwdIANs:", fwdIANs);
        if (this.forwardModifiedToAETs != null && fwdIANs != null) {
            for (Iterator<DicomObject> it = fwdIANs.values().iterator() ; it.hasNext() ;) {
                this.scheduleForward(it.next());
            }
        }
    }
    
    private void addMwlAttrs2Mpps(MppsToMwlLinkResult result) {
        try {
            boolean dcm14xsl = addMwlAttrsToMppsXsl.startsWith("14|");
            java.io.File f = FileUtils.toFile(dcm14xsl ? addMwlAttrsToMppsXsl.substring(3) : addMwlAttrsToMppsXsl);
            if (f.isFile()) {
                Templates tpl = templates.getTemplates(f);
                DicomObject coerce = new BasicDicomObject();
                Templates[] tpls = dcm14xsl ? new Templates[]{dcm2To14Tpl,tpl,dcm14To2Tpl} : new Templates[]{tpl};
                XSLTUtils.xslt(result.getMwl().getAttributes(), tpls, coerce, null);
                List<MPPS> mppss = result.getMppss();
                DicomObject mppsAttrs;
                for (MPPS mpps : mppss) {
                    mppsAttrs = mpps.getAttributes();
                    CoercionUtil.coerceAttributes(mppsAttrs, coerce);
                    lookupMppsToMwlLinkLocal().updateMPPSAttributes(mpps, mppsAttrs);
                }
            } else {
                log.info("Can not add MWL attributes to MPPS Linked notification! addMwlAttrsToMppsXsl stylesheet file not found! file:"+f);
            }
        } catch (Exception e) {
            log.error("Attribute coercion failed! Can not add MWL attributes to MPPS Linked notification!", e);
        }
    }
    public boolean unlinkMpps(long pk) {
        MPPS mpps = lookupMppsToMwlLinkLocal().unlinkMpps(pk, modifyingSystem, modifyReason);
        if (mpps != null) {
            DicomObject mppsAttrs = mpps.getAttributes();
            DicomObject patAttrs = mpps.getPatient().getAttributes();
            StringBuilder sb = new StringBuilder();
            sb.append("Unlink MPPS iuid:").append(mppsAttrs.getString(Tag.SOPInstanceUID)).append(" from SPS ID(s): ");
            DicomElement ssaSQ = mppsAttrs.get(Tag.ScheduledStepAttributesSequence);
            for ( int i = 0, len = ssaSQ.countItems() ; i < len ; i++) {
                sb.append(ssaSQ.getDicomObject(i).getString(Tag.ScheduledProcedureStepID)).append(", ");
            }
            Auditlog.logProcedureRecord(AuditEvent.ActionCode.UPDATE, true, patAttrs, ssaSQ.getDicomObject().getString(Tag.StudyInstanceUID),
                    mpps.getAccessionNumber(), sb.substring(0,sb.length()-2));
            MppsToMwlLinkResult result = new MppsToMwlLinkResult();
            result.addMppsAttributes(mpps);
            this.sendJMXNotification(result);
            if (this.forwardModifiedToAETs != null) {
                this.scheduleForwardByMpps(mpps.getAttributes());
            }
            return true;
        }
        return false;
    }
    
    private void scheduleForwardByMpps(DicomObject mpps) {
        String patId = mpps.getString(Tag.PatientID);
        String studyIuid = mpps.getString(Tag.StudyInstanceUID);
        String seriesIuid, iuid;
        DicomElement mppsSeriesSq = mpps.get(Tag.PerformedSeriesSequence);
        if (mppsSeriesSq != null) {
            for (int i=0, len=mppsSeriesSq.countItems() ; i < len ; i++) {
                DicomObject mppsSeriesItem = mppsSeriesSq.getDicomObject(i);
                DicomElement mppsInstanceSq = mppsSeriesItem.get(Tag.ReferencedImageSequence);
                if (mppsInstanceSq.isEmpty()) 
                    mppsInstanceSq = mppsSeriesItem.get(Tag.ReferencedNonImageCompositeSOPInstanceSequence);
                if (mppsInstanceSq.isEmpty()) {
                    log.warn("Referenced series ("+mppsSeriesItem.getString(Tag.SeriesInstanceUID)+") in MPPS "
                            +mpps.getString(Tag.SOPInstanceUID)+" has no instance reference!");
                    continue;
                }
                seriesIuid = mppsSeriesItem.getString(Tag.SeriesInstanceUID);
                iuid = mppsInstanceSq.getDicomObject(0).getString(Tag.ReferencedSOPInstanceUID);
                scheduleForward(patId, studyIuid, seriesIuid, new String[]{iuid});
            }
        } else {
            log.warn("Forward of modified Object ignored! Reason: Missing PerformedSeriesSequence in MPPS "+mpps);
        }
    }

    public int removeForeignPpsInfo(long studyPk) {
        return this.lookupDicomEditLocal().removeForeignPpsInfo(studyPk);
    }
    
    private Map<String,DicomObject> updateSeriesAttributes(MppsToMwlLinkResult result) throws InstanceNotFoundException, MBeanException, ReflectionException {
        DicomObject coerce = getCoercionAttrs(result.getMwl().getAttributes());
        if ( coerce != null && !coerce.isEmpty()) {
            String[] mppsIuids = new String[result.getMppss().size()];
            int i = 0;
            for (MPPS m : result.getMppss()) {
                mppsIuids[i++] = m.getSopInstanceUID();
            }
            return this.lookupMppsToMwlLinkLocal().updateSeriesAndStudyAttributes(mppsIuids, coerce);
        } else {
            log.warn("No Coercion attributes to update Study and Series Attributes after linking MPPS to MWL! coerce:"+coerce);
            return null;
        }
    }
    private DicomObject getCoercionAttrs(DicomObject ds) throws InstanceNotFoundException, MBeanException, ReflectionException {
        if ( ds == null ) return null;
        log.info("Dataset to get coercion ds:"+ds);
        DicomObject sps = ds.get(Tag.ScheduledProcedureStepSequence).getDicomObject();
        String aet = sps == null ? null : sps.getString(Tag.ScheduledStationAETitle);
        Templates tpl = templates.getTemplatesForAET(aet, MWL2STORE_XSL);
        log.info("found template for aet("+aet+"):"+tpl);
        if (tpl == null) {
            log.warn("Coercion template "+MWL2STORE_XSL+" not found! Can not store MWL attributes to series!");
            return null;
        }
        DicomObject out = new BasicDicomObject();
        try {
            log.info("dcm14Stylesheet:"+dcm14Stylesheet);
            if (dcm14Stylesheet) {
                Templates[] tpls = new Templates[]{dcm2To14Tpl,tpl,dcm14To2Tpl};
                XSLTUtils.xslt(ds, tpls, out, null);
            } else {
                XSLTUtils.xslt(ds, tpl, out, null);
            }
        } catch (Exception e) {
            log.error("Attribute coercion failed:", e);
            return null;
        }
        log.info("return coerced attributes:"+out);
        return out;
    }
    
    private DicomObject[] getRejectionNotes(EntityTree entityTree) {
        Map<Patient, Map<Study, Map<Series, Set<Instance>>>> entityTreeMap = entityTree.getEntityTreeMap();
        DicomObject[] rejNotes = new DicomObject[entityTreeMap.size()];
        int i = 0;
        for ( Map<Study, Map<Series, Set<Instance>>> studies : entityTreeMap.values()) {
            rejNotes[i] = studies.isEmpty() ? null : toRejectionNote(studies);
            log.info("Rejection Note! KOS:"+rejNotes[i++]);
        }
        return rejNotes;
    }
    
    private DicomObject toRejectionNote(Map<Study, Map<Series, Set<Instance>>> entityTree) {
        String suid = forceNewRejNoteStudyIUID ? UIDUtils.createUID() : 
            entityTree.keySet().iterator().next().getStudyInstanceUID(); 
        DicomObject kos = newKeyObject(suid);
        DicomElement crpeSeq = kos.putSequence(Tag.CurrentRequestedProcedureEvidenceSequence);
        entityTree.keySet().iterator().next().getPatient().getAttributes().copyTo(kos);
        for (Map.Entry<Study, Map<Series, Set<Instance>>> entry : entityTree.entrySet() ) {
            addProcedureEvidenceSequenceItem(crpeSeq, entry.getKey(), entry.getValue());
        }
        return kos;
    }
    
    private DicomObject newKeyObject(String studyIUID) {
        DicomObject kos = new BasicDicomObject();
        kos.putString(Tag.StudyInstanceUID,VR.UI, studyIUID);
        kos.putString(Tag.SeriesInstanceUID,VR.UI, UIDUtils.createUID());
        kos.putString(Tag.SOPInstanceUID,VR.UI, UIDUtils.createUID());
        kos.putString(Tag.SOPClassUID,VR.UI, UID.KeyObjectSelectionDocumentStorage);
        kos.putString(Tag.Modality, VR.CS, "KO");
        kos.putInt(Tag.InstanceNumber, VR.IS, 1);
        kos.putDate(Tag.ContentDate, VR.DA, new Date());
        kos.putDate(Tag.ContentTime, VR.TM, new Date());
        DicomElement cncSeq = kos.putSequence(Tag.ConceptNameCodeSequence);
        cncSeq.addDicomObject(rejectNoteCode.toCodeItem());
        kos.putString(Tag.ValueType, VR.CS, "CONTAINER");
        DicomElement tmplSeq = kos.putSequence(Tag.ContentTemplateSequence);
        DicomObject tmplItem = new BasicDicomObject();
        tmplItem.putString(Tag.TemplateIdentifier, VR.CS, "2010");
        tmplItem.putString(Tag.MappingResource, VR.CS, "DCMR");
        tmplSeq.addDicomObject(tmplItem);
        kos.putSequence(Tag.ReferencedPerformedProcedureStepSequence);
        kos.putString(Tag.SeriesDescription, VR.LO, "Rejection Note");
        return kos;
    }
        
    private void addProcedureEvidenceSequenceItem(DicomElement crpeSeq, Study study, Map<Series, Set<Instance>> series) {
        DicomObject item = new BasicDicomObject();
        crpeSeq.addDicomObject(item);
        item.putString(Tag.StudyInstanceUID, VR.UI, study.getStudyInstanceUID());
        DicomElement refSeriesSeq = item.putSequence(Tag.ReferencedSeriesSequence);
        DicomElement refSopSeq;
        DicomObject refSeriesSeqItem, refSopSeqItem;
        for ( Map.Entry<Series, Set<Instance>> instances : series.entrySet()) {
            refSeriesSeqItem = new BasicDicomObject();
            refSeriesSeq.addDicomObject(refSeriesSeqItem);
            refSeriesSeqItem.putString(Tag.SeriesInstanceUID, VR.UI, instances.getKey().getSeriesInstanceUID());
            refSopSeq = refSeriesSeqItem.putSequence(Tag.ReferencedSOPSequence);
            for ( Instance inst : instances.getValue()) {
                refSopSeqItem = new BasicDicomObject();
                refSopSeq.addDicomObject(refSopSeqItem);
                refSopSeqItem.putString(Tag.ReferencedSOPInstanceUID, VR.UI, inst.getSOPInstanceUID());
                refSopSeqItem.putString(Tag.ReferencedSOPClassUID, VR.UI, inst.getSOPClassUID());
            }
        }
    }
    
    private DicomObject makeIAN(Study study, Map<Series, Set<Instance>> mapSeries, Availability availability) {
        log.debug("makeIAN: studyIUID:" + study.getStudyInstanceUID());
        Patient pat = study.getPatient();
        DicomObject ian = new BasicDicomObject();
        ian.putString(Tag.StudyInstanceUID, VR.UI, study.getStudyInstanceUID());
        ian.putString(Tag.AccessionNumber, VR.SH, study.getAccessionNumber());
        ian.putString(Tag.PatientID, VR.LO, pat.getPatientID());
        ian.putString(Tag.IssuerOfPatientID, VR.LO, pat.getIssuerOfPatientID());
        ian.putString(Tag.PatientName, VR.PN, pat.getPatientName());
        DicomElement refPPSSeq = ian.putSequence(Tag.ReferencedPerformedProcedureStepSequence);
        HashSet<String> mppsuids = new HashSet<String>();
        DicomElement refSeriesSeq = ian.putSequence(Tag.ReferencedSeriesSequence);

        for (Map.Entry<Series, Set<Instance>> entry : mapSeries.entrySet() ) {
            Series sl = entry.getKey();
            MPPS mpps = sl.getModalityPerformedProcedureStep();
            if (mpps != null) {
                String mppsuid = mpps.getSopInstanceUID();
                if (mppsuids.add(mppsuid)) {
                    DicomObject refmpps = new BasicDicomObject();
                    refPPSSeq.addDicomObject(refmpps);
                    refmpps.putString(Tag.ReferencedSOPClassUID, VR.UI, UID.ModalityPerformedProcedureStepSOPClass);
                    refmpps.putString(Tag.ReferencedSOPInstanceUID, VR.UI, mppsuid);
                    refmpps.putSequence(Tag.PerformedWorkitemCodeSequence);
                }
            }
            DicomObject refSerItem = new BasicDicomObject();
            refSeriesSeq.addDicomObject(refSerItem);
            refSerItem.putString(Tag.SeriesInstanceUID, VR.UI, sl.getSeriesInstanceUID());
            DicomElement refSopSeq = refSerItem.putSequence(Tag.ReferencedSOPSequence);
            for (Instance instance : entry.getValue()) {
                DicomObject refSopItem = new BasicDicomObject();
                refSopSeq.addDicomObject(refSopItem);
                refSopItem.putString(Tag.RetrieveAETitle, VR.AE, instance.getRetrieveAETs());
                refSopItem.putString(Tag.InstanceAvailability, VR.CS, 
                        availability == null ? instance.getAvailability().name() : availability.name());
                refSopItem.putString(Tag.ReferencedSOPClassUID, VR.UI, instance.getSOPClassUID());
                refSopItem.putString(Tag.ReferencedSOPInstanceUID, VR.UI, instance.getSOPInstanceUID());
            }
        }
        if (log.isDebugEnabled()) {
            log.debug("IAN:"+ ian);
        }
        return ian;
    }
    
    private DicomEditLocal lookupDicomEditLocal() {
        if ( dicomEdit == null ) {
            try {
                InitialContext jndiCtx = new InitialContext();
                dicomEdit = (DicomEditLocal) jndiCtx.lookup(DicomEditLocal.JNDI_NAME);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return dicomEdit;
    }
    
    private MppsToMwlLinkLocal lookupMppsToMwlLinkLocal() {
        if ( mpps2mwl == null ) {
            try {
                InitialContext jndiCtx = new InitialContext();
                mpps2mwl = (MppsToMwlLinkLocal) jndiCtx.lookup(MppsToMwlLinkLocal.JNDI_NAME);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return mpps2mwl;
    }

    private void logPatientDeleted(EntityTree entityTree) {
        Set<Patient> pats = entityTree.getEntityTreeMap().keySet();
        for (Patient pat : pats) {
            Auditlog.logPatientRecord(AuditEvent.ActionCode.DELETE, true, pat.getPatientID(), pat.getPatientName());
        }
    }

    public void logMppsLinkRecord(MppsToMwlLinkResult result ) {
        MWLItem mwl = result.getMwl();
        String accNr = mwl.getAccessionNumber();
        String spsId = mwl.getScheduledProcedureStepID();
        String studyIuid = mwl.getStudyInstanceUID();
        DicomObject patAttrs = mwl.getPatient().getAttributes();
        for ( MPPS mpps : result.getMppss()) {
            String desc = "MPPS "+mpps.getSopInstanceUID()+" linked with MWL entry "+spsId;
            Auditlog.logProcedureRecord(AuditEvent.ActionCode.UPDATE, true, patAttrs, studyIuid, accNr, desc);
        }
    }
    
    private DicomObject[] processStudyDeleted(EntityTree entityTree) throws InstanceNotFoundException, MBeanException, ReflectionException {
        if (entityTree.isEmpty())
            return null;
        DicomObject[] rejNotes = processRejectionNotes(entityTree, true, null);
        processIANs(entityTree, Availability.UNAVAILABLE);
        return rejNotes;
    }

    private DicomObject[] processInstancesDeleted(EntityTree entityTree, String auditDetail) throws InstanceNotFoundException, MBeanException, ReflectionException {
        if (entityTree.isEmpty())
            return null;
        DicomObject[] rejNotes = this.processRejectionNotes(entityTree, false, auditDetail);
        processIANs(entityTree, Availability.UNAVAILABLE);
        return rejNotes;
    }
    
    private DicomObject[] processRejectionNotes(EntityTree entityTree, boolean study, String auditDetails) 
                throws InstanceNotFoundException, MBeanException, ReflectionException {
        DicomObject[] rejNotes = getRejectionNotes(entityTree);
        for (DicomObject kos : rejNotes) {
            if (kos == null)
                continue;
            if (study) {
                Auditlog.logStudyDeleted(kos, true);
            } else {
                Auditlog.logInstancesAccessed(AuditEvent.ActionCode.DELETE, true, kos, true, auditDetails);
            }
            processRejectionNote(kos);
        }
        return rejNotes;
    }
    
    private void processRejectionNote(DicomObject rejNote) throws InstanceNotFoundException, MBeanException, ReflectionException {
        if (processRejNote) {
            log.info("RejectionNote KOS:"+rejNote);
            server.invoke(rejNoteServiceName, "scheduleRejectionNote", 
                    new Object[]{rejNote}, new String[]{DicomObject.class.getName()});
        }
    }

    private ArrayList<DicomObject> getIANs(EntityTree entityTree, Availability availability) throws InstanceNotFoundException, MBeanException, ReflectionException {
        ArrayList<DicomObject> ians = new ArrayList<DicomObject>();
        Map<Patient, Map<Study, Map<Series, Set<Instance>>>> entityTreeMap = entityTree.getEntityTreeMap();
        for (Map<Study, Map<Series, Set<Instance>>> studyMap : entityTreeMap.values()) {
            for ( Map.Entry<Study, Map<Series, Set<Instance>>> studyEntry: studyMap.entrySet()) {
                ians.add(makeIAN(studyEntry.getKey(), studyEntry.getValue(), availability));
            }
        }
        return ians;
    }
    
    private ArrayList<DicomObject> processIANs(EntityTree entityTree, Availability availability) throws InstanceNotFoundException, MBeanException, ReflectionException {
        if (processIAN) {
            ArrayList<DicomObject> ians = getIANs(entityTree, availability);
            for (DicomObject ian : ians) {
                server.invoke(ianScuServiceName, "scheduleIAN", 
                        new Object[]{ian}, new String[]{DicomObject.class.getName()});
            }
            return ians;
        }
        return new ArrayList<DicomObject>();
    }

    public void sendJMXNotification(Object o) {
        if (log.isDebugEnabled()) {
            log.debug("Send JMX Notification: " + o);
        }
        long eventID = super.getNextNotificationSequenceNumber();
        Notification notif = new Notification(o.getClass().getName(), this,
                eventID);
        notif.setUserData(o);
        super.sendNotification(notif);
    }
    
    private void sendDicomActionNotification(DicomObject obj, String action, String level) {
        DicomActionNotification notif = new DicomActionNotification(this, obj, action, level, getNextNotificationSequenceNumber());
        log.debug("Send JMX Notification:"+notif);
        super.sendNotification(notif);
    }
    
    public void doAfterDicomEdit(String patId, String patName, String[] studyIUIDs, DicomObject obj, String qrLevel) {
        sendDicomActionNotification(obj, DicomActionNotification.UPDATE, qrLevel);
        if ("PATIENT".equals(qrLevel)) {
            Auditlog.logPatientRecord(AuditEvent.ActionCode.UPDATE, true, patId, patName);
       } else {
            Auditlog.logDicomObjectUpdated(true, patId, patName, studyIUIDs, obj, "Dicom Attributes updated on "+qrLevel+" level!");
            if ("STUDY".equals(qrLevel) || "SERIES".equals(qrLevel) || "IMAGE".equals(qrLevel)) {
                obj.putString(Tag.QueryRetrieveLevel, VR.CS, qrLevel);
                try {
                    server.invoke(attrModScuServiceName, "scheduleModification", 
                            new Object[]{obj}, new String[]{DicomObject.class.getName()});
                } catch (Exception e) {
                    log.error("Scheduling Attributes Modification Notification failed!", e);
                }
                if (forwardModifiedToAETs != null) {
                    DicomObject fwdIan = lookupDicomEditLocal().getIanForForwardModifiedObject(obj, qrLevel);
                    scheduleForward(fwdIan);
                }
            } else {
                log.debug("No further action after Dicom Edit defined for level "+qrLevel+"!");
            }
        }        
    }

    private void scheduleForward(DicomObject fwdIan) {
        log.info("fwdIan:"+fwdIan);
        if (fwdIan == null) {
            log.warn("Forward of modified Object ignored! Reason: No ONLINE or NEARLINE instance found!");
        } else {
            for (int i = 0 ; i < forwardModifiedToAETs.length ; i++) {
                try {
                    log.info("Scheduling forward of modified object to {}", forwardModifiedToAETs[i]);
                    server.invoke(moveScuServiceName, "scheduleMoveInstances", 
                            new Object[]{fwdIan, forwardModifiedToAETs[i], null}, 
                            new String[]{DicomObject.class.getName(), STRING, Integer.class.getName()});
                } catch (Exception e) {
                    log.error("Scheduling forward of modified object to "+forwardModifiedToAETs[i]+" failed!", e);
                }
            }
        }
    }
    private void scheduleForward(String patId, String studyIuid, String seriesIuid, String[] iuids) {
        for (int i = 0 ; i < forwardModifiedToAETs.length ; i++) {
            try {
                server.invoke(moveScuServiceName, "scheduleMoveInstances", 
                        new Object[]{patId, studyIuid, seriesIuid, iuids, null, forwardModifiedToAETs[i], null}, 
                        new String[]{STRING, STRING, STRING, String[].class.getName(), 
                            STRING, STRING, Integer.class.getName()});
            } catch (Exception e) {
                log.error("Scheduling forward of modified object to "+forwardModifiedToAETs[i]+" failed!", e);
            }
        }
    }

}

