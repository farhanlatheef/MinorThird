package edu.cmu.minorthird.text;

import java.io.Serializable;
import java.util.*;

/** Maintains assertions about 'types' and 'properties' of
 * contiguous Spans of these TextToken's.
 *
 * @author William Cohen
*/

public class BasicTextLabels implements MutableTextLabels, Serializable
{
  static private final long serialVersionUID = 1;
	private final int CURRENT_VERSION_NUMBER = 1;

	private static final Set EMPTY_SET = new HashSet();

	private Map textTokenPropertyMap = new HashMap();
	private Set textTokenPropertySet = new HashSet();
	private Map spanPropertyMap = new HashMap();
	private Set spanPropertySet = new HashSet();
	private Map typeDocumentSetMap = new TreeMap();
	private Map closureDocumentSetMap = new HashMap();
	private Map textTokenDictMap = new HashMap();
	private Set annotatedBySet = new HashSet();
	private Map detailMap = new TreeMap();

  // don't serialize this
	transient private TextBase textBase = null;

	public BasicTextLabels() { this.textBase = null; }
	public BasicTextLabels(TextBase textBase) { this.textBase = textBase; }

	public TextBase getTextBase() { return textBase; }

	public void setTextBase(TextBase textBase) {
		if (this.textBase!=null) throw new IllegalStateException("textBase already set");
		this.textBase = textBase;
	}

	/** A convenience method which creates empty labels
	 * containing a single string.
	 */
	public BasicTextLabels(String s) {
		this(new BasicTextBase());
		getTextBase().loadDocument("nullId",s);
	}

	//
	// maintain annotation history
	//
	public boolean isAnnotatedBy(String s) { return annotatedBySet.contains(s); }

	public void setAnnotatedBy(String s) { annotatedBySet.add(s); }



	//
	// maintain dictionaries
	//

	/** Returns true if the value of the Token is in the named dictionary. */
	public boolean inDict(Token token,String dictName) {
		if (token.getValue()==null) throw new IllegalArgumentException("null token.value?");
		Set set = (Set)textTokenDictMap.get(dictName);
		if (set==null) throw new IllegalArgumentException("undefined dictionary "+dictName);
		return set.contains(token.getValue());
	}

	/** Associate a dictionary with this labeling. */
	public void defineDictionary(String dictName, Set dictionary) {
		textTokenDictMap.put(dictName,dictionary);
	}


	//
	// maintain assertions about properties of Tokens
	//

	/** Get the property value associated with this Token.  */
	public String getProperty(Token token,String prop) {
		return (String)getPropMap(token).get(prop);
	}

	/** Get a set of all properties.  */
	public Set getTokenProperties() {
		return textTokenPropertySet;
	}

	/** Assert that Token textToken has the given value of the given property */
	public void setProperty(Token textToken,String prop,String value) {
		getPropMap(textToken).put(prop,value);
		textTokenPropertySet.add(prop);
	}

	private TreeMap getPropMap(Token textToken) {
		TreeMap map = (TreeMap)textTokenPropertyMap.get(textToken);
		if (map==null) {
			map = new TreeMap();
			textTokenPropertyMap.put(textToken,map);
		}
		return map;
	}

	//
	// maintain assertions about properties of spans
	//

	/** Get the property value associated with this Span.  */
	public String getProperty(Span span,String prop) {
		return (String)getPropMap(span).get(prop);
	}

	/** Get a set of all properties.  */
	public Set getSpanProperties() {
		return spanPropertySet;
	}

	/** Assert that Span span has the given value of the given property */
	public void setProperty(Span span,String prop,String value) {
		getPropMap(span).put(prop,value);
		spanPropertySet.add(prop);
	}

	private TreeMap getPropMap(Span span) {
		TreeMap map = (TreeMap)spanPropertyMap.get(span);
		if (map==null) {
			map = new TreeMap();
			spanPropertyMap.put(span,map);
		}
		return map;
	}

	//
	// maintain assertions about types of Spans
	//
	public boolean hasType(Span span,String type)	{
		return getTypeSet(type,span.getDocumentId()).contains(span);
	}

	public void addToType(Span span,String type) {
		if (type==null) throw new IllegalArgumentException("null type added");
		getTypeSet(type,span.getDocumentId()).add(span);
	}
	public void addToType(Span span,String type,Details details) {
		if (details==null) throw new IllegalArgumentException("details can't be null!");
		if (type==null) throw new IllegalArgumentException("null type added");
		addToType(span,type);
		detailMap.put(new SpanTypeKey(span,type), details);
	}
	public Set getTypes() {
		return typeDocumentSetMap.keySet();
	}
	public boolean isType(String type) {
		return typeDocumentSetMap.get(type)!=null;
	}
	public void declareType(String type) {
		if (type==null) throw new IllegalArgumentException("null type declared");
		if (!isType(type)) typeDocumentSetMap.put(type, new TreeMap());
	}

	public Span.Looper instanceIterator(String type) {
		return new MyNestedSpanLooper(type,false);
	}
	public Span.Looper instanceIterator(String type,String documentId) {
		if (documentId!=null) return new BasicSpanLooper( getTypeSet(type,documentId) );
		else return instanceIterator(type);
	}

	public void defineTypeInside(String type,Span s,Span.Looper i) {
		if (type==null || s.getDocumentId()==null) throw new IllegalArgumentException("null type defined");
		//System.out.println("BTE type: "+type+" documentId: "+s.getDocumentId());
		Set set = getTypeSet(type,s.getDocumentId());
		// remove all spans currently inside set
		for (Iterator j=set.iterator(); j.hasNext(); ) {
			Span t = (Span)j.next();
			if (s.contains(t)) j.remove();
		}
		// add spans from i to set
		while (i.hasNext()) set.add( i.nextSpan() );
		// close the type
		closeTypeInside(type,s);
	}

	public Details getDetails(Span span,String type) {
		SpanTypeKey key = new SpanTypeKey(span,type);
		Details details = (Details)detailMap.get(key);
		if (details!=null) return details;
		else return hasType(span,type) ? Details.DEFAULT : null;
	}

	// get the set of spans with a given type in the given document
	protected Set getTypeSet(String type,String documentId) {
		if (type==null || documentId==null) throw new IllegalArgumentException("null type?");
		TreeMap documentsWithType = (TreeMap)typeDocumentSetMap.get(type);
		if (documentsWithType==null) {
			typeDocumentSetMap.put( type, (documentsWithType = new TreeMap()) );
		}
		//System.out.println("BTE type: "+type+" documentId: "+documentId+" documentsWithType:" + documentsWithType);
		TreeSet set = (TreeSet)documentsWithType.get(documentId);
		if (set==null) {
			documentsWithType.put( documentId, (set = new TreeSet()) );
		}
		return set;
	}

	private class SpanTypeKey implements Comparable {
		public Span span;
		public String type;
		public SpanTypeKey(Span span,String type) { this.span=span; this.type=type; }
		public int compareTo(Object obj) {
			SpanTypeKey b = (SpanTypeKey)obj;
			int tmp = span.compareTo(b.span);
			if (tmp!=0) return tmp;
			return type.compareTo(b.type);
		}
	}

	//
	// maintain assertions about where the closed world assumption holds
	//

	public Span.Looper closureIterator(String type) {
		return new MyNestedSpanLooper(type,true);
	}

	public Span.Looper closureIterator(String type,String documentId) {
		if (documentId!=null)
			return new BasicSpanLooper(getClosureSet(type,documentId).iterator());
		else
			return closureIterator(type);
	}

	public void closeTypeInside(String type,Span s) {
		getClosureSet(type,s.getDocumentId()).add(s);
	}


	// get the set of spans with a given type in the given document
	private Set getClosureSet(String type,String documentId) {
		TreeMap documentsWithClosure = (TreeMap)closureDocumentSetMap.get(type);
		if (documentsWithClosure==null) {
			closureDocumentSetMap.put( type, (documentsWithClosure = new TreeMap()) );
		}
		TreeSet set = (TreeSet)documentsWithClosure.get(documentId);
		if (set==null) {
			documentsWithClosure.put( documentId, (set = new TreeSet()) );
		}
		return set;
	}


	// iterate over all spans of a given type
	private class MyNestedSpanLooper implements Span.Looper {
		private Iterator documentIterator;
		private Iterator spanIterator;
		private Span nextSpan;
		private boolean getClosures; // if false, get documents
		public MyNestedSpanLooper(String type,boolean getClosures) {
			//System.out.println("building MyNestedSpanLooper for "+type+": "+typeDocumentSetMap);
			Map documentMap = (Map) (getClosures? closureDocumentSetMap.get(type) : typeDocumentSetMap.get(type));
			if (documentMap==null) {
				nextSpan = null;
			} else {
				documentIterator = documentMap.entrySet().iterator();
				spanIterator = null;
				advance();
			}
		}
		public int estimatedSize() {
			return -1;
		}
		public boolean hasNext() {
			return nextSpan!=null;
		}
		public void remove() {
			throw new UnsupportedOperationException("can't remove");
		}
		public Object next() {
			Span result = nextSpan;
			advance();
			return result;
		}
		public Span nextSpan() {
			return (Span)next();
		}
		private void advance() {
			if (spanIterator!=null && spanIterator.hasNext()) {
				// get next span in the current document
				nextSpan = (Span)spanIterator.next();
			} else if (documentIterator.hasNext()) {
				// move to the next document
				Map.Entry entry = (Map.Entry)documentIterator.next();
				spanIterator = ((TreeSet)entry.getValue()).iterator();
				advance();
			} else {
				// nothing found
				nextSpan = null;
			}
		}
	}

	public String toString() {
		return "[BasicTextLabels "+typeDocumentSetMap+"]";
	}

	/** Dump of all strings that have textTokenuence with the given property */
	public String showTokenProp(TextBase base,String prop) {
		StringBuffer buf = new StringBuffer();
		for (Span.Looper i = base.documentSpanIterator(); i.hasNext(); ) {
			Span span = i.nextSpan();
			for (int j=0; j<span.size(); j++) {
				Token textToken = span.getToken(j);
				if (j>0) buf.append(" ");
				buf.append( textToken.getValue() );
				String val = getProperty(textToken,prop);
				if (val!=null) {
					buf.append(":"+val);
				}
			}
			buf.append("\n");
		}
		return buf.toString();
	}
}