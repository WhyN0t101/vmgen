"""OVF 1.0 descriptor generation for OVA export."""


def generate_ovf(vm_name: str, cpu_count: int, memory_mb: int,
                 disk_capacity_bytes: int, vmdk_size_bytes: int) -> str:
    """
    Generate a minimal OVF 1.0 descriptor compatible with VMware Workstation and VirtualBox.

    NetworkSection declares "NAT" as the network name — both hypervisors recognise this
    keyword natively and attach the NIC to their NAT adapter without prompting.
    The Connection element in the NIC item must reference a name declared here;
    omitting the NetworkSection (or using an undeclared name) causes VMware to reject
    the import with "Invalid value for element Connection".

    Declares a lsilogic SCSI controller (InstanceID 4) that the disk item references
    as its parent — required for VMware/VirtualBox to enumerate and boot the disk.

    OperatingSystemSection (ovf:id="94", CIM code for "Linux 2.6.x (64-bit)") is required
    for VirtualBox specifically: without it, VirtualBox imports the VM as guest type
    "Other/Unknown" and applies generic hardware defaults (notably IO-APIC left disabled),
    which hangs a multi-vCPU Linux guest before it reaches GRUB -- a black screen with a
    blinking cursor and no boot output at all. VMware Workstation boots fine either way;
    this section costs nothing there. Confirmed by reproducing the hang, then confirming
    a manual guest-type correction in an already-imported VM's settings fixed it.
    """
    return f'''<?xml version="1.0" encoding="UTF-8"?>
<Envelope xmlns="http://schemas.dmtf.org/ovf/envelope/1"
          xmlns:ovf="http://schemas.dmtf.org/ovf/envelope/1"
          xmlns:rasd="http://schemas.dmtf.org/wbem/wscim/1/cim-schema/2/CIM_ResourceAllocationSettingData"
          xmlns:vssd="http://schemas.dmtf.org/wbem/wscim/1/cim-schema/2/CIM_VirtualSystemSettingData">
  <References>
    <File ovf:id="disk1" ovf:href="disk.vmdk" ovf:size="{vmdk_size_bytes}"/>
  </References>
  <NetworkSection>
    <Info>Available virtual networks</Info>
    <Network ovf:name="NAT">
      <Description>NAT network — provides outbound connectivity via the host</Description>
    </Network>
  </NetworkSection>
  <DiskSection>
    <Info>Virtual disk information</Info>
    <Disk ovf:diskId="disk1" ovf:fileRef="disk1"
          ovf:capacity="{disk_capacity_bytes}"
          ovf:format="http://www.vmware.com/interfaces/specifications/vmdk.html#streamOptimized"/>
  </DiskSection>
  <VirtualSystem ovf:id="{vm_name}">
    <Info>VMGen generated vulnerable VM</Info>
    <OperatingSystemSection ovf:id="94">
      <Info>Guest Operating System</Info>
      <Description>Ubuntu Linux (64-bit)</Description>
    </OperatingSystemSection>
    <VirtualHardwareSection>
      <Info>Virtual hardware requirements</Info>
      <System>
        <vssd:ElementName>Virtual Hardware Family</vssd:ElementName>
        <vssd:InstanceID>0</vssd:InstanceID>
        <vssd:VirtualSystemType>vmx-13</vssd:VirtualSystemType>
      </System>
      <Item>
        <rasd:Description>Number of virtual CPUs</rasd:Description>
        <rasd:ElementName>{cpu_count} virtual CPU(s)</rasd:ElementName>
        <rasd:InstanceID>1</rasd:InstanceID>
        <rasd:ResourceType>3</rasd:ResourceType>
        <rasd:VirtualQuantity>{cpu_count}</rasd:VirtualQuantity>
      </Item>
      <Item>
        <rasd:AllocationUnits>MegaBytes</rasd:AllocationUnits>
        <rasd:Description>Memory Size</rasd:Description>
        <rasd:ElementName>{memory_mb} MB of memory</rasd:ElementName>
        <rasd:InstanceID>2</rasd:InstanceID>
        <rasd:ResourceType>4</rasd:ResourceType>
        <rasd:VirtualQuantity>{memory_mb}</rasd:VirtualQuantity>
      </Item>
      <Item>
        <rasd:Address>0</rasd:Address>
        <rasd:Description>SCSI Controller</rasd:Description>
        <rasd:ElementName>SCSI Controller 0</rasd:ElementName>
        <rasd:InstanceID>4</rasd:InstanceID>
        <rasd:ResourceSubType>lsilogic</rasd:ResourceSubType>
        <rasd:ResourceType>6</rasd:ResourceType>
      </Item>
      <Item>
        <rasd:AddressOnParent>0</rasd:AddressOnParent>
        <rasd:ElementName>Hard Disk</rasd:ElementName>
        <rasd:HostResource>ovf:/disk/disk1</rasd:HostResource>
        <rasd:InstanceID>3</rasd:InstanceID>
        <rasd:Parent>4</rasd:Parent>
        <rasd:ResourceType>17</rasd:ResourceType>
      </Item>
      <Item>
        <rasd:AutomaticAllocation>true</rasd:AutomaticAllocation>
        <rasd:Connection>NAT</rasd:Connection>
        <rasd:Description>Ethernet adapter on NAT network</rasd:Description>
        <rasd:ElementName>Ethernet 0</rasd:ElementName>
        <rasd:InstanceID>5</rasd:InstanceID>
        <rasd:ResourceSubType>E1000</rasd:ResourceSubType>
        <rasd:ResourceType>10</rasd:ResourceType>
      </Item>
    </VirtualHardwareSection>
  </VirtualSystem>
</Envelope>'''
