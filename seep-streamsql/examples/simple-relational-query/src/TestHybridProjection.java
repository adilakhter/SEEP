import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Set;

import uk.ac.imperial.lsds.seep.multi.IMicroOperatorCode;
import uk.ac.imperial.lsds.seep.multi.ITupleSchema;
import uk.ac.imperial.lsds.seep.multi.MicroOperator;
import uk.ac.imperial.lsds.seep.multi.MultiOperator;
import uk.ac.imperial.lsds.seep.multi.SubQuery;
import uk.ac.imperial.lsds.seep.multi.TupleSchema;
import uk.ac.imperial.lsds.seep.multi.Utils;
import uk.ac.imperial.lsds.seep.multi.WindowDefinition;
import uk.ac.imperial.lsds.streamsql.expressions.Expression;
import uk.ac.imperial.lsds.streamsql.expressions.eint.IntColumnReference;
import uk.ac.imperial.lsds.streamsql.expressions.elong.LongColumnReference;
import uk.ac.imperial.lsds.streamsql.op.gpu.stateless.AProjectionKernel;
import uk.ac.imperial.lsds.streamsql.op.stateless.Projection;

public class TestHybridProjection {

	public static void main(String [] args) {
		
		String filename = args[0];
		
		WindowDefinition window = 
			new WindowDefinition (Utils.TYPE, Utils.RANGE, Utils.SLIDE);
		
		ITupleSchema schema = new TupleSchema (Utils.OFFSETS, Utils._TUPLE_);
		
		IMicroOperatorCode projectionCPUCode = new Projection (
			new Expression [] {
				new LongColumnReference(0),
				new IntColumnReference(1),
				new IntColumnReference(2),
				new IntColumnReference(3),
				new IntColumnReference(4),
				new IntColumnReference(5),
				new IntColumnReference(6)
			}
		);
		
		IMicroOperatorCode projectionGPUCode = new AProjectionKernel (
			new Expression [] {
				new LongColumnReference(0),
				new IntColumnReference(1),
				new IntColumnReference(2),
				new IntColumnReference(3),
				new IntColumnReference(4),
				new IntColumnReference(5),
				new IntColumnReference(6)
			},
			schema,
			filename
		);
		
		MicroOperator uoperator = new MicroOperator (projectionCPUCode, projectionGPUCode, 1);
		
		/* Query */
		Set<MicroOperator> operators = new HashSet<MicroOperator>();
		operators.add(uoperator);
		
		Set<SubQuery> queries = new HashSet<SubQuery>();
		SubQuery query = new SubQuery (0, operators, schema, window);
		queries.add(query);
			
		MultiOperator operator = new MultiOperator(queries, 0);
		operator.setup();
		
		byte [] data = new byte [Utils.BUNDLE];
		ByteBuffer b = ByteBuffer.wrap(data);
		while (b.hasRemaining())
			b.putInt(1);
		try {
			while (true) {
				operator.processData (data);
				// Thread.sleep(100L);
			}
		} catch (Exception e) { 
			e.printStackTrace(); 
			System.exit(1);
		}
	}
}
