/*
* Copyright (c) 2016 Wipro Ltd. and others. All rights reserved.
*
* This program and the accompanying materials are made available under the
* terms of the Eclipse Public License v1.0 which accompanies this distribution,
* and is available at http://www.eclipse.org/legal/epl-v10.html
*/

package org.opendaylight.mwtn.template;

import org.opendaylight.controller.sal.binding.api.BindingAwareBroker.ProviderContext;
import org.opendaylight.controller.sal.binding.api.BindingAwareBroker.RpcRegistration;
import com.google.common.base.Optional;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.MountPoint;
import org.opendaylight.controller.md.sal.binding.api.MountPointService;
import org.opendaylight.controller.sal.binding.api.BindingAwareProvider;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.network.topology.topology.topology.types.TopologyNetconf;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.template.rev180119.TemplateService;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TopologyId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TemplateProvider implements MountpointChangeListener, BindingAwareProvider, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(TemplateProvider.class);

    private static final InstanceIdentifier<Topology> NETCONF_TOPO_IID = InstanceIdentifier
            .create(NetworkTopology.class)
            .child(Topology.class, new TopologyKey(new TopologyId(TopologyNetconf.QNAME.getLocalName())));

    private RpcRegistration<TemplateService> templateService;
    private ProviderContext session;
    private DataBroker dataBroker;
    private DataChangeHandler dataChangeHandleronfSubscriptionManager;

    @Override
    public void onSessionInitiated(ProviderContext pSession) {

        this.session = pSession;
        this.dataBroker = pSession.getSALService(DataBroker.class);

        LOG.info("TemplateProvider Session Initiated");
        templateService = session.addRpcImplementation(TemplateService.class, new TemplateServiceImpl());

        // netconfSubscriptionManager should be the last because calling back this service
        this.dataChangeHandleronfSubscriptionManager = new DataChangeHandler(this, dataBroker);
        this.dataChangeHandleronfSubscriptionManager.register();

    }

    @Override
    public void close() throws Exception {
        LOG.info("TemplateProvider Closed");
        if (templateService != null) {
            templateService.close();
        }
    }

    @Override
    public void removeListenerOnNode(NodeId nNodeId, NetconfNode nNode) {
        LOG.info("Remove event listener on Netconf device :: Name : {}", nNodeId.getValue());
    }

    @Override
    public void startListenerOnNode(NodeId nNodeId, NetconfNode nNode) {

        String mountPointNodeName = nNodeId.getValue();
        LOG.info("Starting event listener on Netconf device :: Name : {}", mountPointNodeName);

        MountPointService mountService = session.getSALService(MountPointService.class);

        InstanceIdentifier<Node> instanceIdentifier = NETCONF_TOPO_IID.child(Node.class,
                new NodeKey(new NodeId(mountPointNodeName)));

        Optional<MountPoint> optionalMountPoint = null;
        int timeout = 10000;
        while ( !(optionalMountPoint = mountService.getMountPoint(instanceIdentifier)).isPresent() && timeout > 0) {

            LOG.info("Event listener waiting for mount point for Netconf device :: Name : {}", mountPointNodeName);
            try {
                Thread.sleep(1000);
                timeout -= 1000;
            } catch (InterruptedException e) {
                LOG.info("Event listener waiting for mount point for Netconf device :: Name : {} Time: {}", mountPointNodeName, timeout);
            }
        }

        if (!optionalMountPoint.isPresent()) {
            LOG.warn("Event listener timeout while waiting for mount point for Netconf device :: Name : {} ", mountPointNodeName);
            return;
        }

        //Mountpoint is present for sure
        MountPoint mountPoint = optionalMountPoint.get();

        DataBroker netconfNodeDataBroker = mountPoint.getService(DataBroker.class).get();
        LOG.info("Databroker service 1:{} 2:{}", dataBroker.hashCode(), netconfNodeDataBroker.hashCode());


    }

    @Override
    public void mountpointNodeCreation(NodeId nNodeId, NetconfNode nNode) {
        LOG.info("Create mountpoint for Netconf device :: Name : {}", nNodeId.getValue());
    }

    @Override
    public void mountpointNodeRemoved(NodeId nNodeId) {
        LOG.info("Remove mountpoint for Netconf device :: Name : {}", nNodeId.getValue());
    }
}
