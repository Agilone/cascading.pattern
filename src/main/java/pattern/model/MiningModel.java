/*
 * Copyright (c) 2007-2013 Concurrent, Inc. All Rights Reserved.
 *
 * Project and contact information: http://www.concurrentinc.com/
 */

package pattern.model;


import cascading.tuple.Fields;
import cascading.tuple.Tuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import pattern.PMML;
import pattern.PatternException;
import pattern.model.tree.Context;
import pattern.model.tree.TreeModel;

import javax.xml.xpath.XPathConstants;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class MiningModel extends Model implements Serializable
  {
  /** Field LOG */
  private static final Logger LOG = LoggerFactory.getLogger( MiningModel.class );

  public Context context = null;
  public List<Model> segments = new ArrayList<Model>();
  public String multipleModelMethod = null;
  public Map<String, Integer> votes = new HashMap<String, Integer>();

  /**
   * @param pmml PMML model
   * @throws PatternException
   */
  public MiningModel( PMML pmml ) throws PatternException
    {
    this.schema = pmml.getSchema();
    this.context = new Context();

    schema.parseMiningSchema( pmml.getNodeList( "/PMML/MiningModel/MiningSchema/MiningField" ) );

    String nodeMultiMethod = "/PMML/MiningModel/Segmentation/@multipleModelMethod";
    multipleModelMethod =  pmml.getReader().read( nodeMultiMethod, XPathConstants.STRING ).toString();


    String expr = "/PMML/MiningModel/Segmentation/Segment";
    NodeList node_list = pmml.getNodeList( expr );

    for( int i = 0; i < node_list.getLength(); i++ )
      {
      Node node = node_list.item( i );

      if( node.getNodeType() == Node.ELEMENT_NODE )
        {
        TreeModel tree_model = new TreeModel( pmml, context, node );
        segments.add( tree_model );
        }
      }
    }

  /**
   * Prepare to classify with this model. Called immediately before
   * the enclosing Operation instance is put into play processing
   * Tuples.
   */
  @Override
  public void prepare()
    {
    context.prepare( schema );
    }

  /**
   * Classify an input tuple, returning the predicted label.
   *
   *
   * @param values tuple values
   * @param fields field names
   * @return String
   * @throws PatternException
   */
  @Override
  public String classifyTuple( Tuple values, Fields fields ) throws PatternException
    {
    Boolean[] pred_eval = context.evalPredicates( schema, values );
    String label = null;
    Integer winning_vote = 0;

    votes.clear();

    // tally the vote for each tree in the forest

    for( Model model : segments )
      {
      label = ((TreeModel) model).tree.traverse( pred_eval );

      if( !votes.containsKey( label ) )
        winning_vote = 1;
      else
        winning_vote = votes.get( label ) + 1;

      votes.put( label, winning_vote );
      }

    if(multipleModelMethod.equals("average"))
      {
        Double total = 0.0;
        int count = 0;

        for( String key: votes.keySet())
          {
            total += Double.parseDouble(key)*votes.get(key);
            count+=votes.get(key);

          }
        Double value = total/count;
        label = value.toString();
      }
    // determine the winning label (majorityVote)
    else
      {
      for( String key : votes.keySet() )
        {
        if( votes.get( key ) > winning_vote )
          {
          label = key;
          winning_vote = votes.get( key );
          }
        }
      }


    return label;
    }

  /** @return String  */
  @Override
  public String toString()
    {
    StringBuilder buf = new StringBuilder();

    if( schema != null )
      {
      buf.append( schema );
      buf.append( "\n" );
      buf.append( "---------" );
      buf.append( "\n" );
      }

    if( context != null )
      {
      buf.append( context );
      buf.append( "\n" );
      buf.append( "---------" );
      buf.append( "\n" );
      }

    buf.append( "segments: " );
    buf.append( segments );
    buf.append( "---------" );
    buf.append( "\n" );

    for( Model model : segments )
      {
      buf.append( ((TreeModel) model).tree );
      buf.append( "\n" );
      }

    buf.append( "---------" );
    buf.append( "\n" );
    buf.append( "votes: " );
    buf.append( votes );

    return buf.toString();
    }
  }
