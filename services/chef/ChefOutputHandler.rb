class Cloudify::ChefOutputHandler < Chef::Handler
  def initialize(output_file)
    @output_file = output_file
  end

  def report
    File.open(@output_file, "w") do |f|
      f.write merge_attributes(run_status.node)
    end
  end
  
  private
  def merge_attributes(node)
    case node
    when ::Chef::Node::Attribute, ::Chef::Node
      node = node.to_hash
    else
      return node
    end
    return node.inject(Hash.new) do |h, (k, v)|
      h[k] = merge_attributes(v)
    end
  end

end
