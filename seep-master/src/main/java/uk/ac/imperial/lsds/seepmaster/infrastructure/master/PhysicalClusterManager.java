package uk.ac.imperial.lsds.seepmaster.infrastructure.master;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Set;

import uk.ac.imperial.lsds.seep.infrastructure.EndPoint;
import uk.ac.imperial.lsds.seep.infrastructure.ExecutionUnitType;
import uk.ac.imperial.lsds.seep.infrastructure.Node;

public class PhysicalClusterManager implements InfrastructureManager {
	
	public final ExecutionUnitType executionUnitType = ExecutionUnitType.PHYSICAL_NODE;
	private Deque<ExecutionUnit> physicalNodes;

	@Override
	public ExecutionUnit buildExecutionUnit(EndPoint ep) {
		return new PhysicalNode(ep);
	}
	
//	@Override
//	public ExecutionUnitType getExecutionUnitType() {
//		return executionUnitType;
//	}
	
	@Override
	public void addExecutionUnit(ExecutionUnit eu) {
		physicalNodes.push(eu);
	}
	
	@Override
	public ExecutionUnit getExecutionUnit(){
		return physicalNodes.pop();
	}

	@Override
	public boolean removeExecutionUnit(int id) {
		for(ExecutionUnit eu : physicalNodes){
			if(eu.getId() == id){
				physicalNodes.remove(eu);
				return true;
			}
		}
		return false;
	}

	@Override
	public int executionUnitsAvailable() {
		return physicalNodes.size();
	}

	@Override
	public void claimExecutionUnits(int numExecutionUnits) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void decommisionExecutionUnits(int numExecutionUnits) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void decommisionExecutionUnit(ExecutionUnit eu) {
		// TODO Auto-generated method stub
		
	}

}